package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class WebSocketAutoStartupService {

    private static final long START_INTERVAL_MS = 1500L;

    private final XianyuAccountMapper accountMapper;
    private final XianyuCookieMapper cookieMapper;
    private final WebSocketService webSocketService;

    public WebSocketAutoStartupService(
            XianyuAccountMapper accountMapper,
            XianyuCookieMapper cookieMapper,
            WebSocketService webSocketService
    ) {
        this.accountMapper = accountMapper;
        this.cookieMapper = cookieMapper;
        this.webSocketService = webSocketService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void restoreConnections() {
        List<XianyuAccount> accounts = accountMapper.selectList(null);
        if (accounts == null || accounts.isEmpty()) {
            log.info("应用启动后未发现闲鱼账号，跳过自动连接");
            return;
        }

        int started = 0;
        int skipped = 0;
        for (XianyuAccount account : accounts) {
            if (account == null || account.getId() == null || !hasCookie(account.getId())) {
                skipped++;
                continue;
            }
            try {
                if (webSocketService.isConnected(account.getId())) {
                    skipped++;
                    continue;
                }
                boolean success = webSocketService.startWebSocket(account.getId());
                if (success) {
                    started++;
                }
            } catch (Exception e) {
                log.warn("应用启动自动连接账号失败: accountId={}, reason={}", account.getId(), e.getMessage());
            }
            sleepBetweenAccounts();
        }
        log.info("应用启动自动连接完成: started={}, skipped={}, total={}", started, skipped, accounts.size());
    }

    private boolean hasCookie(Long accountId) {
        XianyuCookie cookie = cookieMapper.selectOne(new LambdaQueryWrapper<XianyuCookie>()
                .eq(XianyuCookie::getXianyuAccountId, accountId)
                .isNotNull(XianyuCookie::getCookieText)
                .ne(XianyuCookie::getCookieText, "")
                .orderByDesc(XianyuCookie::getCreatedTime)
                .last("LIMIT 1"));
        return cookie != null;
    }

    private void sleepBetweenAccounts() {
        try {
            Thread.sleep(START_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
