package com.feijimiao.xianyuassistant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SliderTrajectoryLearner {

    private static final Path HISTORY_DIR = Paths.get("dbdata", "trajectory_history");
    private static final long EXPIRY_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_RECORDS_PER_ACCOUNT = 200;
    private static final double EXPLORATION_RATE = 0.15;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, List<TrajectoryRecord>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, double[]> strategyWeights = new ConcurrentHashMap<>();

    public void recordSuccess(long accountId, String profileName, TrajectoryParams params) {
        record(accountId, profileName, params, true);
    }

    public void recordFailure(long accountId, String profileName, TrajectoryParams params) {
        record(accountId, profileName, params, false);
    }

    /**
     * 基于历史数据推荐策略名称。无数据时返回 null（由调用方 fallback 到默认逻辑）。
     */
    public String recommendProfile(long accountId) {
        List<TrajectoryRecord> records = getRecords(accountId);
        if (records.isEmpty()) return null;

        Map<String, long[]> stats = new HashMap<>();
        for (TrajectoryRecord r : records) {
            stats.computeIfAbsent(r.getProfileName(), k -> new long[2]);
            long[] counts = stats.get(r.getProfileName());
            counts[0]++;
            if (r.isSuccess()) counts[1]++;
        }

        if (ThreadLocalRandom.current().nextDouble() < EXPLORATION_RATE) {
            return null;
        }

        return softmaxSelect(stats);
    }

    public TrajectoryParams getSuccessParams(long accountId, String profileName) {
        List<TrajectoryRecord> records = getRecords(accountId);
        List<TrajectoryRecord> successes = records.stream()
                .filter(r -> r.isSuccess() && r.getProfileName().equals(profileName))
                .toList();
        if (successes.isEmpty()) return null;

        TrajectoryRecord picked = successes.get(ThreadLocalRandom.current().nextInt(successes.size()));
        return jitter(picked.getParams());
    }

    private void record(long accountId, String profileName, TrajectoryParams params, boolean success) {
        TrajectoryRecord record = new TrajectoryRecord();
        record.setProfileName(profileName);
        record.setParams(params);
        record.setSuccess(success);
        record.setTimestamp(System.currentTimeMillis());

        List<TrajectoryRecord> records = cache.computeIfAbsent(accountId, k -> loadFromDisk(k));
        synchronized (records) {
            records.add(record);
            evict(records);
        }
        persistAsync(accountId, records);
        log.debug("[TrajectoryLearner] 记录: accountId={}, profile={}, success={}", accountId, profileName, success);
    }

    private List<TrajectoryRecord> getRecords(long accountId) {
        List<TrajectoryRecord> records = cache.computeIfAbsent(accountId, this::loadFromDisk);
        synchronized (records) {
            evict(records);
        }
        return records;
    }

    private void evict(List<TrajectoryRecord> records) {
        long cutoff = System.currentTimeMillis() - EXPIRY_MS;
        records.removeIf(r -> r.getTimestamp() < cutoff);
        while (records.size() > MAX_RECORDS_PER_ACCOUNT) {
            records.remove(0);
        }
    }

    private String softmaxSelect(Map<String, long[]> stats) {
        double temperature = 1.5;
        List<Map.Entry<String, long[]>> entries = new ArrayList<>(stats.entrySet());
        double[] scores = new double[entries.size()];
        double maxScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < entries.size(); i++) {
            long[] counts = entries.get(i).getValue();
            scores[i] = (double) counts[1] / Math.max(1, counts[0]);
            if (scores[i] > maxScore) maxScore = scores[i];
        }

        double sumExp = 0;
        double[] exps = new double[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            exps[i] = Math.exp((scores[i] - maxScore) / temperature);
            sumExp += exps[i];
        }

        double pick = ThreadLocalRandom.current().nextDouble() * sumExp;
        double cumulative = 0;
        for (int i = 0; i < entries.size(); i++) {
            cumulative += exps[i];
            if (pick <= cumulative) {
                return entries.get(i).getKey();
            }
        }
        return entries.get(entries.size() - 1).getKey();
    }

    private TrajectoryParams jitter(TrajectoryParams base) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        TrajectoryParams p = new TrajectoryParams();
        p.setOvershootRatio(base.getOvershootRatio() * r.nextDouble(0.92, 1.08));
        p.setBaseDelayMs(base.getBaseDelayMs() * r.nextDouble(0.88, 1.12));
        p.setAccelerationCurve(base.getAccelerationCurve() * r.nextDouble(0.93, 1.07));
        p.setYJitter(base.getYJitter() * r.nextDouble(0.85, 1.15));
        p.setSteps(Math.max(18, base.getSteps() + r.nextInt(-3, 4)));
        return p;
    }

    private List<TrajectoryRecord> loadFromDisk(long accountId) {
        Path file = HISTORY_DIR.resolve(accountId + ".json");
        if (!Files.exists(file)) return Collections.synchronizedList(new ArrayList<>());
        try {
            List<TrajectoryRecord> records = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
            return Collections.synchronizedList(new ArrayList<>(records));
        } catch (IOException e) {
            log.warn("[TrajectoryLearner] 读取历史失败: accountId={}", accountId, e);
            return Collections.synchronizedList(new ArrayList<>());
        }
    }

    private void persistAsync(long accountId, List<TrajectoryRecord> records) {
        try {
            Files.createDirectories(HISTORY_DIR);
            Path file = HISTORY_DIR.resolve(accountId + ".json");
            synchronized (records) {
                objectMapper.writeValue(file.toFile(), new ArrayList<>(records));
            }
        } catch (IOException e) {
            log.warn("[TrajectoryLearner] 持久化失败: accountId={}", accountId, e);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrajectoryRecord {
        private String profileName;
        private TrajectoryParams params;
        private boolean success;
        private long timestamp;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrajectoryParams {
        private double overshootRatio;
        private double baseDelayMs;
        private double accelerationCurve;
        private double yJitter;
        private int steps;
    }
}