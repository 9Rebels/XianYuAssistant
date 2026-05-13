package com.feijimiao.xianyuassistant.service;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
final class SliderTrajectoryPlanner {
    private static final int[] PERM = buildPermutation();
    private static final List<TrajectoryProfile> STRATEGIES = List.of(
            new TrajectoryProfile("conservative", 1.01, 1.06, 28, 40, 10D, 20D, 1.80, 2.40, 0.80, 2.00, 0.08),
            new TrajectoryProfile("standard", 1.03, 1.10, 22, 35, 6D, 15D, 1.50, 2.10, 1.20, 2.80, 0.57),
            new TrajectoryProfile("aggressive", 1.06, 1.15, 18, 30, 4D, 12D, 1.30, 1.90, 1.50, 3.20, 0.35),
            new TrajectoryProfile("rapid", 1.80, 2.20, 5, 8, 0.2D, 0.6D, 1.30, 1.80, 1.00, 3.00, 0.00)
    );

    private SliderTrajectoryLearner learner;
    private long accountId;

    void setLearner(SliderTrajectoryLearner learner, long accountId) {
        this.learner = learner;
        this.accountId = accountId;
    }

    void recordResult(String profileName, double overshootRatio, double baseDelayMs, double curve, double yJitter, int steps, boolean success) {
        if (learner == null) return;
        SliderTrajectoryLearner.TrajectoryParams params = new SliderTrajectoryLearner.TrajectoryParams();
        params.setOvershootRatio(overshootRatio);
        params.setBaseDelayMs(baseDelayMs);
        params.setAccelerationCurve(curve);
        params.setYJitter(yJitter);
        params.setSteps(steps);
        if (success) {
            learner.recordSuccess(accountId, profileName, params);
        } else {
            learner.recordFailure(accountId, profileName, params);
        }
    }

    TrajectoryPlan plan(double distance, int attempt) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        TrajectoryProfile profile = selectProfile(attempt, random);
        int baseSteps = random.nextInt(profile.getMinSteps(), profile.getMaxSteps() + 1);
        int steps = profile.getMinOvershootRatio() > 1.5 ? baseSteps : fittsSteps(distance, baseSteps);
        double overshootRatio = random.nextDouble(profile.getMinOvershootRatio(), profile.getMaxOvershootRatio());
        double baseDelayMs = random.nextDouble(profile.getMinBaseDelayMs(), profile.getMaxBaseDelayMs());
        double curve = random.nextDouble(profile.getMinAccelerationCurve(), profile.getMaxAccelerationCurve());
        double yJitter = random.nextDouble(profile.getMinYJitter(), profile.getMaxYJitter());
        List<TrajectoryPoint> points = buildPoints(distance, steps, overshootRatio, baseDelayMs, curve, yJitter, random);
        log.info("生成无头滑块轨迹: attempt={}, profile={}, fittsSteps={}, points={}, overshoot={}%, baseDelayMs={}, curve={}",
                attempt,
                profile.getName(),
                steps,
                points.size(),
                Math.round((overshootRatio - 1D) * 1000D) / 10D,
                Math.round(baseDelayMs * 10D) / 10D,
                Math.round(curve * 100D) / 100D);
        return new TrajectoryPlan(profile.getName(), points, overshootRatio, baseDelayMs, curve);
    }

    static double perlinNoise1d(double x, double seedOffset) {
        int xi = (int) Math.floor(x) & 255;
        double xf = x - Math.floor(x);
        double u = fade(xf);
        int idx = (xi + (int) seedOffset) & 255;
        int a = PERM[idx];
        int b = PERM[idx + 1];
        return lerp(grad1d(a, xf), grad1d(b, xf - 1D), u);
    }

    private TrajectoryProfile selectProfile(int attempt, ThreadLocalRandom random) {
        if (learner != null) {
            String recommended = learner.recommendProfile(accountId);
            if (recommended != null) {
                for (TrajectoryProfile s : STRATEGIES) {
                    if (s.getName().equals(recommended)) {
                        log.info("轨迹学习推荐策略: {}", recommended);
                        return s;
                    }
                }
            }
        }
        // 第1次尝试：80% 概率使用 rapid（极速一气呵成，模拟人类"拖到最右边"的自然行为）
        if (attempt == 1 && random.nextDouble() < 0.80) {
            return STRATEGIES.get(3); // rapid
        }
        if (attempt == 2) {
            // 第2次：50% rapid, 30% aggressive, 20% standard
            double pick = random.nextDouble();
            if (pick < 0.50) return STRATEGIES.get(3); // rapid
            if (pick < 0.80) return STRATEGIES.get(2); // aggressive
            return STRATEGIES.get(1); // standard
        }
        if (attempt >= 3) {
            // 第3次及以后：混合策略
            double pick = random.nextDouble();
            if (pick < 0.40) return STRATEGIES.get(3); // rapid
            if (pick < 0.70) return STRATEGIES.get(2); // aggressive
            return STRATEGIES.get(0); // conservative
        }
        return weightedStrategy(random);
    }

    private TrajectoryProfile weightedStrategy(ThreadLocalRandom random) {
        double pick = random.nextDouble();
        double cursor = 0D;
        for (TrajectoryProfile strategy : STRATEGIES) {
            cursor += strategy.getWeight();
            if (pick <= cursor) {
                return strategy;
            }
        }
        return STRATEGIES.get(1);
    }

    private List<TrajectoryPoint> buildRapidPoints(double distance, int steps, double baseDelayMs,
                                                   double curve, double yJitter, ThreadLocalRandom random) {
        List<TrajectoryPoint> points = new ArrayList<>();
        // 三次贝塞尔曲线控制点（和参考项目一致）
        double p1 = distance * random.nextDouble(0.20, 0.35);
        double p2 = distance * random.nextDouble(0.70, 0.85);
        double ySeed = random.nextDouble(0, 1000);

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            // ease-out: 开始快，结束慢
            double eased = 1.0 - Math.pow(1.0 - t, curve);
            // 三次贝塞尔
            double b0 = Math.pow(1 - eased, 3) * 0;
            double b1 = 3 * Math.pow(1 - eased, 2) * eased * p1;
            double b2 = 3 * (1 - eased) * eased * eased * p2;
            double b3 = Math.pow(eased, 3) * distance;
            double x = b0 + b1 + b2 + b3 + random.nextDouble(-0.5, 0.5);
            // Perlin Y 轴抖动
            double y = perlinNoise1d(t * 4.0, ySeed) * yJitter * 0.65
                    + perlinNoise1d(t * 8.0, ySeed + 500) * yJitter * 0.35;
            // 延迟：中间快两端慢
            double speedFactor = Math.max(0.1, Math.sin(t * Math.PI));
            double delay = baseDelayMs / speedFactor + random.nextDouble(0, baseDelayMs * 0.3);
            points.add(new TrajectoryPoint(x, y, delay));
        }
        return points;
    }

    private int fittsSteps(double distance, int baseSteps) {
        double fittsFactor = Math.log(Math.max(1D, distance / 50D + 1D)) / Math.log(7D);
        int scaled = (int) Math.round(baseSteps * clamp(fittsFactor, 0.7D, 1.3D));
        return (int) clamp(scaled, 18D, 45D);
    }

    private List<TrajectoryPoint> buildPoints(double distance,
                                              int steps,
                                              double overshootRatio,
                                              double baseDelayMs,
                                              double curve,
                                              double yJitter,
                                              ThreadLocalRandom random) {
        // rapid 模式：极速单向滑动，无回退，鼠标移动距离远超实际需要（模拟人类"用力一甩"）
        if (overshootRatio > 1.5) {
            return buildRapidPoints(distance * overshootRatio, steps, baseDelayMs, curve, yJitter, random);
        }
        List<TrajectoryPoint> points = new ArrayList<>();
        double overshootTarget = distance * overshootRatio;
        int mainSteps = Math.max(14, (int) Math.round(steps * 0.75D));
        int retreatSteps = Math.max(4, steps - mainSteps);
        double p1 = overshootTarget * random.nextDouble(0.20D, 0.35D);
        double p2 = overshootTarget * random.nextDouble(0.70D, 0.85D);
        NoiseSeeds seeds = new NoiseSeeds(random.nextDouble(0D, 1000D), random.nextDouble(0D, 1000D),
                random.nextDouble(2D, 4D), random.nextDouble(6D, 10D), random.nextDouble(0D, 1000D));
        double prevY = 0D;
        for (int i = 1; i <= mainSteps; i++) {
            double t = (double) i / mainSteps;
            double eased = 1D - Math.pow(1D - t, curve);
            double x = cubicBezier(0D, p1, p2, overshootTarget, eased) + random.nextDouble(-0.5D, 0.5D);
            double y = perlinOctaves1d(t * seeds.getLowFrequency(), 2, 0.5D, seeds.getLowSeed()) * yJitter * 0.65D
                    + perlinNoise1d(t * seeds.getHighFrequency(), seeds.getHighSeed()) * yJitter * 0.35D
                    + random.nextDouble(-0.2D, 0.2D);
            double delay = perlinDelayMs(t, baseDelayMs, seeds.getDelaySeed(), random);
            points.add(new TrajectoryPoint(x, y, delay));
            prevY = y;
        }
        appendRetreat(points, distance, overshootTarget, retreatSteps, prevY, yJitter, baseDelayMs, random);
        appendFineTune(points, distance, yJitter, baseDelayMs, random);
        return points;
    }

    private double perlinDelayMs(double t, double baseDelayMs, double delaySeed, ThreadLocalRandom random) {
        double speedFactor = Math.max(0.1D, Math.sin(t * Math.PI));
        double delayJitter = 1D + perlinNoise1d(t * 5D, delaySeed) * 0.15D;
        double delay = baseDelayMs / speedFactor * delayJitter;
        if (t > 0.2D && t < 0.8D && random.nextDouble() < 0.08D) {
            delay += random.nextDouble(10D, 30D);
        }
        return Math.max(1D, delay);
    }

    private void appendRetreat(List<TrajectoryPoint> points,
                               double distance,
                               double overshootTarget,
                               int retreatSteps,
                               double prevY,
                               double yJitter,
                               double baseDelayMs,
                               ThreadLocalRandom random) {
        double retreatDistance = overshootTarget - distance;
        if (retreatDistance <= 0D) {
            return;
        }
        for (int i = 1; i <= retreatSteps; i++) {
            double t = (double) i / retreatSteps;
            double eased = t * t * (3D - 2D * t);
            double x = overshootTarget - retreatDistance * eased + random.nextDouble(-0.3D, 0.3D);
            double y = prevY * (1D - t) + random.nextDouble(-yJitter * 0.3D, yJitter * 0.3D);
            points.add(new TrajectoryPoint(x, y, baseDelayMs * random.nextDouble(1.2D, 1.8D)));
        }
    }

    private void appendFineTune(List<TrajectoryPoint> points,
                                double distance,
                                double yJitter,
                                double baseDelayMs,
                                ThreadLocalRandom random) {
        int fineTuneCount = random.nextInt(1, 4);
        for (int i = 0; i < fineTuneCount; i++) {
            points.add(new TrajectoryPoint(
                    distance + random.nextDouble(-1.5D, 1.5D),
                    random.nextDouble(-yJitter * 0.2D, yJitter * 0.2D),
                    baseDelayMs * random.nextDouble(0.8D, 1.5D)
            ));
        }
        points.add(new TrajectoryPoint(distance + random.nextDouble(-0.5D, 0.5D),
                random.nextDouble(-0.2D, 0.2D), baseDelayMs * random.nextDouble(0.5D, 1.0D)));
    }

    private static double perlinOctaves1d(double x, int octaves, double persistence, double seedOffset) {
        double total = 0D;
        double amplitude = 1D;
        double frequency = 1D;
        double maxAmplitude = 0D;
        for (int i = 0; i < octaves; i++) {
            total += perlinNoise1d(x * frequency, seedOffset) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= 2D;
        }
        return maxAmplitude > 0D ? total / maxAmplitude : 0D;
    }

    private static int[] buildPermutation() {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            values.add(i);
        }
        Collections.shuffle(values, new Random(System.nanoTime()));
        int[] result = new int[512];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i & 255);
        }
        return result;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6D - 15D) + 10D);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double grad1d(int hash, double x) {
        return (hash & 1) == 0 ? x : -x;
    }

    private static double cubicBezier(double p0, double p1, double p2, double p3, double t) {
        double u = 1D - t;
        return u * u * u * p0 + 3D * u * u * t * p1 + 3D * u * t * t * p2 + t * t * t * p3;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Value
    static class TrajectoryPlan {
        String profileName;
        List<TrajectoryPoint> points;
        double overshootRatio;
        double baseDelayMs;
        double accelerationCurve;
    }

    @Value
    static class TrajectoryPoint {
        double x;
        double y;
        double delayMs;
    }

    @Value
    private static class NoiseSeeds {
        double lowSeed;
        double highSeed;
        double lowFrequency;
        double highFrequency;
        double delaySeed;
    }

    @Value
    private static class TrajectoryProfile {
        String name;
        double minOvershootRatio;
        double maxOvershootRatio;
        int minSteps;
        int maxSteps;
        double minBaseDelayMs;
        double maxBaseDelayMs;
        double minAccelerationCurve;
        double maxAccelerationCurve;
        double minYJitter;
        double maxYJitter;
        double weight;
    }
}
