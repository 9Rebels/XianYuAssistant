package com.feijimiao.xianyuassistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 清理账号删除后的业务数据。
 */
@Slf4j
@Service
public class AccountDataCleanupService {

    private final JdbcTemplate jdbcTemplate;

    public AccountDataCleanupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> deleteAccountAndRelatedData(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("账号ID不能为空");
        }

        Map<String, Integer> deleted = new LinkedHashMap<>();
        delete(deleted, "xianyu_kami_usage_record",
                "DELETE FROM xianyu_kami_usage_record WHERE xianyu_account_id = ?", accountId);
        delete(deleted, "xianyu_kami_item",
                """
                DELETE FROM xianyu_kami_item
                WHERE kami_config_id IN (
                    SELECT id FROM xianyu_kami_config WHERE xianyu_account_id = ?
                )
                """, accountId);

        deleteByAccountId(deleted, "xianyu_cookie", accountId);
        deleteByAccountId(deleted, "xianyu_chat_message", accountId);
        deleteByAccountId(deleted, "xianyu_conversation_state", accountId);
        deleteByAccountId(deleted, "xianyu_goods_config", accountId);
        deleteByAccountId(deleted, "xianyu_goods_auto_delivery_config", accountId);
        deleteByAccountId(deleted, "xianyu_goods_order", accountId);
        deleteByAccountId(deleted, "xianyu_order", accountId);
        deleteByAccountId(deleted, "xianyu_item_polish_task", accountId);
        deleteByAccountId(deleted, "xianyu_goods_auto_reply_record", accountId);
        deleteByAccountId(deleted, "xianyu_auto_reply_rule", accountId);
        deleteByAccountId(deleted, "xianyu_operation_log", accountId);
        deleteByAccountId(deleted, "xianyu_kami_config", accountId);
        deleteByAccountId(deleted, "xianyu_media_library", accountId);
        deleteByAccountId(deleted, "xianyu_poi_cache", accountId);
        deleteByAccountId(deleted, "xianyu_publish_schedule", accountId);
        deleteByAccountId(deleted, "xianyu_goods", accountId);
        delete(deleted, "xianyu_account", "DELETE FROM xianyu_account WHERE id = ?", accountId);

        log.info("账号及关联数据删除完成: accountId={}, deleted={}", accountId, deleted);
        return deleted;
    }

    private void deleteByAccountId(Map<String, Integer> deleted, String tableName, Long accountId) {
        delete(deleted, tableName, "DELETE FROM " + tableName + " WHERE xianyu_account_id = ?", accountId);
    }

    private void delete(Map<String, Integer> deleted, String tableName, String sql, Long accountId) {
        int count = jdbcTemplate.update(sql, accountId);
        deleted.put(tableName, count);
        log.info("删除账号关联数据: accountId={}, table={}, count={}", accountId, tableName, count);
    }
}
