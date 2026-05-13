package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.service.NotificationService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 自动发货本地统计报表
 */
@Slf4j
@Component
public class AutoDeliveryReportScheduler {

    private static final String KEY_HOURLY_REPORT_ENABLED = "notify_hourly_report_enabled";

    @Autowired
    private SysSettingService sysSettingService;

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private NotificationService notificationService;

    @Scheduled(cron = "0 0 * * * ?")
    public void sendHourlyReport() {
        if (!"1".equals(sysSettingService.getSettingValue(KEY_HOURLY_REPORT_ENABLED))) {
            return;
        }
        String today = LocalDate.now().toString();
        String content = "时间: " + LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
                + "\n待发货: " + orderMapper.countPendingDelivery()
                + "\n今日成功: " + orderMapper.countDeliverySuccessByDate(today)
                + "\n今日失败: " + orderMapper.countDeliveryFailByDate(today);
        log.info("发送自动发货整点报表");
        notificationService.notifyEvent(NotificationService.EVENT_HOURLY_REPORT, "自动发货整点报表", content);
    }
}
