package com.feijimiao.xianyuassistant.event.account;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 账号删除事件
 *
 * <p>由 {@code AccountService.deleteAccountAndRelatedData} 在删 DB 前同步发布。
 * 同步监听器需要在事件返回前完成对该 accountId 相关资源（WebSocket 连接、缓存、浏览器实例、
 * Token 锁、人机验证状态等）的释放，避免账号删除后留下幽灵任务反复重连。</p>
 *
 * <p>监听者必须自行 try/catch 异常，不要让单个监听器失败阻断后续清理。</p>
 */
@Getter
public class AccountRemovedEvent extends ApplicationEvent {

    private final Long accountId;

    public AccountRemovedEvent(Object source, Long accountId) {
        super(source);
        this.accountId = accountId;
    }
}
