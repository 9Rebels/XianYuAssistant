package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 操作记录控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/operation-log")
@CrossOrigin(origins = "*")
public class OperationLogController {
    
    @Autowired
    private OperationLogService operationLogService;

    private static final int DEFAULT_RUNTIME_LOG_LINES = 200;
    private static final int MAX_RUNTIME_LOG_LINES = 2000;
    
    /**
     * 查询操作记录
     */
    @PostMapping("/query")
    public ResultObject<Map<String, Object>> queryLogs(@RequestBody QueryLogsReqDTO reqDTO) {
        try {
            log.info("查询操作记录: accountId={}, type={}, module={}, status={}, page={}, pageSize={}",
                    reqDTO.getAccountId(), reqDTO.getOperationType(), reqDTO.getOperationModule(),
                    reqDTO.getOperationStatus(), reqDTO.getPage(), reqDTO.getPageSize());
            
            if (reqDTO.getAccountId() == null) {
                return ResultObject.failed("账号ID不能为空");
            }
            
            // 设置默认值
            if (reqDTO.getPage() == null || reqDTO.getPage() < 1) {
                reqDTO.setPage(1);
            }
            if (reqDTO.getPageSize() == null || reqDTO.getPageSize() < 1) {
                reqDTO.setPageSize(20);
            }
            
            Map<String, Object> result = operationLogService.queryLogs(
                    reqDTO.getAccountId(),
                    reqDTO.getOperationType(),
                    reqDTO.getOperationModule(),
                    reqDTO.getOperationStatus(),
                    reqDTO.getPage(),
                    reqDTO.getPageSize()
            );
            
            // 添加调试日志
            log.info("查询结果: total={}, logs={}", result.get("total"), 
                    result.get("logs") != null ? ((java.util.List<?>) result.get("logs")).size() : 0);
            
            return ResultObject.success(result);
            
        } catch (Exception e) {
            log.error("查询操作记录失败", e);
            return ResultObject.failed("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除旧日志
     */
    @PostMapping("/deleteOld")
    public ResultObject<Integer> deleteOldLogs(@RequestBody DeleteOldLogsReqDTO reqDTO) {
        try {
            log.info("删除旧操作记录: days={}", reqDTO.getDays());
            
            if (reqDTO.getDays() == null || reqDTO.getDays() < 1) {
                return ResultObject.failed("天数必须大于0");
            }
            
            int deleted = operationLogService.deleteOldLogs(reqDTO.getDays());
            
            return ResultObject.success(deleted);
            
        } catch (Exception e) {
            log.error("删除旧操作记录失败", e);
            return ResultObject.failed("删除失败: " + e.getMessage());
        }
    }

    /**
     * 查询软件运行日志，读取项目logs目录下的日志文件。
     */
    @PostMapping("/runtime/files")
    public ResultObject<Map<String, Object>> runtimeLogFiles() {
        try {
            Path logsRoot = Paths.get("logs").toAbsolutePath().normalize();
            List<Map<String, Object>> files = listRuntimeLogFiles(logsRoot);
            Map<String, Object> result = new HashMap<>();
            result.put("files", files);
            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("读取运行日志文件列表失败", e);
            return ResultObject.failed("读取运行日志文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 查询软件运行日志，读取项目logs目录下的日志文件。
     */
    @PostMapping("/runtime")
    public ResultObject<Map<String, Object>> runtimeLogs(@RequestBody RuntimeLogReqDTO reqDTO) {
        try {
            int lines = reqDTO.getLines() == null ? DEFAULT_RUNTIME_LOG_LINES : reqDTO.getLines();
            lines = Math.max(1, Math.min(MAX_RUNTIME_LOG_LINES, lines));
            boolean full = Boolean.TRUE.equals(reqDTO.getFull());

            Path logsRoot = Paths.get("logs").toAbsolutePath().normalize();
            Path logFile = resolveRuntimeLogFile(logsRoot, reqDTO.getFile());
            if (logFile == null || !Files.exists(logFile)) {
                return ResultObject.failed("未找到运行日志文件");
            }

            List<String> content = full
                    ? readAllLines(logFile, StandardCharsets.UTF_8)
                    : readLastLines(logFile, lines, StandardCharsets.UTF_8);
            Collections.reverse(content);
            Map<String, Object> result = new HashMap<>();
            result.put("file", logsRoot.relativize(logFile).toString().replace("\\", "/"));
            result.put("lastModified", Files.getLastModifiedTime(logFile).toMillis());
            result.put("size", Files.size(logFile));
            result.put("lines", content);
            result.put("full", full);
            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("读取运行日志失败", e);
            return ResultObject.failed("读取运行日志失败: " + e.getMessage());
        }
    }

    /**
     * 清空当前运行日志文件，只截断logs目录下的.log文件，不删除文件。
     */
    @PostMapping("/runtime/clear")
    public ResultObject<Map<String, Object>> clearRuntimeLogs(@RequestBody RuntimeLogReqDTO reqDTO) {
        try {
            Path logsRoot = Paths.get("logs").toAbsolutePath().normalize();
            Path logFile = resolveRuntimeLogFile(logsRoot, reqDTO.getFile());
            if (logFile == null || !Files.exists(logFile)) {
                return ResultObject.failed("未找到运行日志文件");
            }

            Files.writeString(logFile, "", StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            Map<String, Object> result = new HashMap<>();
            result.put("file", logsRoot.relativize(logFile).toString().replace("\\", "/"));
            result.put("lastModified", Files.getLastModifiedTime(logFile).toMillis());
            result.put("size", Files.size(logFile));
            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("清空运行日志失败", e);
            return ResultObject.failed("清空运行日志失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> listRuntimeLogFiles(Path logsRoot) throws IOException {
        if (!Files.exists(logsRoot)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(logsRoot, 3)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".log"))
                    .sorted(Comparator.comparingLong(this::getLastModifiedMillis).reversed())
                    .forEach(path -> {
                        try {
                            Map<String, Object> item = new HashMap<>();
                            item.put("file", logsRoot.relativize(path).toString().replace("\\", "/"));
                            item.put("lastModified", Files.getLastModifiedTime(path).toMillis());
                            item.put("size", Files.size(path));
                            files.add(item);
                        } catch (IOException e) {
                            log.warn("读取日志文件信息失败: {}", path, e);
                        }
                    });
        }
        return files;
    }

    private Path resolveRuntimeLogFile(Path logsRoot, String file) throws IOException {
        if (file != null && !file.trim().isEmpty()) {
            Path candidate = logsRoot.resolve(file.trim()).normalize();
            if (!candidate.startsWith(logsRoot) || !candidate.getFileName().toString().endsWith(".log")) {
                throw new IllegalArgumentException("非法日志文件");
            }
            return candidate;
        }

        try (Stream<Path> stream = Files.walk(logsRoot, 3)) {
            List<Path> logFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".log"))
                    .toList();

            return logFiles.stream()
                    .filter(path -> "all.log".equals(path.getFileName().toString()))
                    .max(Comparator.comparingLong(this::getLastModifiedMillis))
                    .orElseGet(() -> logFiles.stream()
                            .max(Comparator.comparingLong(this::getLastModifiedMillis))
                            .orElse(null));
        }
    }

    private long getLastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private List<String> readLastLines(Path logFile, int maxLines, Charset charset) throws IOException {
        ArrayDeque<String> deque = new ArrayDeque<>(maxLines);
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(logFile), decoder))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (deque.size() == maxLines) {
                    deque.removeFirst();
                }
                deque.addLast(line);
            }
        }
        return new ArrayList<>(deque);
    }

    private List<String> readAllLines(Path logFile, Charset charset) throws IOException {
        List<String> lines = new ArrayList<>();
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(logFile), decoder))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
    
    /**
     * 查询操作记录请求DTO
     */
    @Data
    public static class QueryLogsReqDTO {
        private Long accountId;           // 账号ID（必填）
        private String operationType;     // 操作类型（可选）
        private String operationModule;   // 操作模块（可选）
        private Integer operationStatus;  // 操作状态（可选）
        private Integer page;             // 页码（默认1）
        private Integer pageSize;         // 每页数量（默认20）
    }
    
    /**
     * 删除旧日志请求DTO
     */
    @Data
    public static class DeleteOldLogsReqDTO {
        private Integer days;  // 删除多少天之前的日志
    }

    /**
     * 查询运行日志请求DTO
     */
    @Data
    public static class RuntimeLogReqDTO {
        private String file;    // logs目录下的相对路径，可选
        private Integer lines;  // 读取最后多少行
        private Boolean full;   // 是否读取完整日志
    }
}
