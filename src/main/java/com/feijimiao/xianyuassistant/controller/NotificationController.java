package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuNotificationLog;
import com.feijimiao.xianyuassistant.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知日志控制器
 */
@RestController
@RequestMapping("/api/notification")
@CrossOrigin(origins = "*")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping("/logs")
    public ResultObject<List<XianyuNotificationLog>> latestLogs() {
        return ResultObject.success(notificationService.latestLogs());
    }

    @PostMapping("/latest")
    public ResultObject<List<XianyuNotificationLog>> latest(@RequestBody(required = false) LatestNotificationReqDTO reqDTO) {
        int limit = reqDTO != null && reqDTO.getLimit() != null && reqDTO.getLimit() > 0 ? reqDTO.getLimit() : 20;
        Long afterId = reqDTO != null ? reqDTO.getAfterId() : null;
        List<XianyuNotificationLog> logs = notificationService.latestLogs().stream()
                .filter(log -> afterId == null || log.getId() > afterId)
                .limit(limit)
                .collect(Collectors.toList());
        return ResultObject.success(logs);
    }

    @PostMapping("/test")
    public ResultObject<String> test() {
        boolean success = notificationService.sendTest("闲鱼助手测试通知", "这是一条通知通道测试消息");
        if (success) {
            return ResultObject.success("测试通知已发送");
        }
        String error = notificationService.latestLogs().stream()
                .filter(log -> "test".equals(log.getEventType()))
                .findFirst()
                .map(XianyuNotificationLog::getErrorMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .orElse("请检查通知配置");
        return ResultObject.failed("测试通知发送失败：" + error);
    }

    public static class LatestNotificationReqDTO {
        private Long afterId;
        private Integer limit;

        public Long getAfterId() {
            return afterId;
        }

        public void setAfterId(Long afterId) {
            this.afterId = afterId;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }
    }
}
