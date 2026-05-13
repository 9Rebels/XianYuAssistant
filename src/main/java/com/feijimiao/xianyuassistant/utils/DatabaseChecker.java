package com.feijimiao.xianyuassistant.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * 数据库检查工具类
 */
@Slf4j
@Component
public class DatabaseChecker {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void checkAutoDeliveryConfigData() {
        try {
            // 先检查表是否存在
            String checkTableSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='xianyu_goods_auto_delivery_config'";
            List<Map<String, Object>> tableExists = jdbcTemplate.queryForList(checkTableSql);
            
            if (tableExists.isEmpty()) {
                log.info("自动发货配置表不存在,跳过数据检查(表将在后续自动创建)");
                return;
            }
            
            // 检查自动发货配置表中的数据
            String sql = "SELECT id, xianyu_account_id, xy_goods_id, create_time, update_time FROM xianyu_goods_auto_delivery_config LIMIT 5";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            log.info("自动发货配置表数据检查:");
            if (results.isEmpty()) {
                log.info("  表中暂无数据");
            } else {
                for (Map<String, Object> row : results) {
                    log.info("  ID: {}, 账号ID: {}, 商品ID: {}, 创建时间: {}, 更新时间: {}", 
                            row.get("id"), 
                            row.get("xianyu_account_id"), 
                            row.get("xy_goods_id"), 
                            row.get("create_time"), 
                            row.get("update_time"));
                }
            }
        } catch (Exception e) {
            log.warn("检查自动发货配置表数据时出现异常: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void addAutoDeliveryImageUrlColumn() {
        try {
            String checkTableSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='xianyu_goods_auto_delivery_config'";
            List<Map<String, Object>> tableExists = jdbcTemplate.queryForList(checkTableSql);

            if (tableExists.isEmpty()) {
                return;
            }

            String checkColumnSql = "PRAGMA table_info(xianyu_goods_auto_delivery_config)";
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(checkColumnSql);
            boolean hasImageUrl = columns.stream()
                    .anyMatch(col -> "auto_delivery_image_url".equals(col.get("name")));
            boolean hasPostDeliveryText = columns.stream()
                    .anyMatch(col -> "post_delivery_text".equals(col.get("name")));

            if (!hasImageUrl) {
                jdbcTemplate.execute("ALTER TABLE xianyu_goods_auto_delivery_config ADD COLUMN auto_delivery_image_url TEXT");
                log.info("已为xianyu_goods_auto_delivery_config表添加auto_delivery_image_url列");
            }
            if (!hasPostDeliveryText) {
                jdbcTemplate.execute("ALTER TABLE xianyu_goods_auto_delivery_config ADD COLUMN post_delivery_text TEXT");
                log.info("已为xianyu_goods_auto_delivery_config表添加post_delivery_text列");
            }
        } catch (Exception e) {
            log.warn("添加自动发货扩展列时出现异常: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void addApiDeliveryColumns() {
        try {
            String checkTableSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='xianyu_goods_auto_delivery_config'";
            List<Map<String, Object>> tableExists = jdbcTemplate.queryForList(checkTableSql);
            if (tableExists.isEmpty()) {
                return;
            }

            String checkColumnSql = "PRAGMA table_info(xianyu_goods_auto_delivery_config)";
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(checkColumnSql);
            boolean hasRequestExtras = columns.stream().anyMatch(col -> "api_request_extras".equals(col.get("name")));
            boolean hasDeliveryTemplate = columns.stream().anyMatch(col -> "api_delivery_template".equals(col.get("name")));

            if (!hasRequestExtras) {
                jdbcTemplate.execute("ALTER TABLE xianyu_goods_auto_delivery_config ADD COLUMN api_request_extras TEXT");
                log.info("已为xianyu_goods_auto_delivery_config表添加api_request_extras列");
            }
            if (!hasDeliveryTemplate) {
                jdbcTemplate.execute("ALTER TABLE xianyu_goods_auto_delivery_config ADD COLUMN api_delivery_template TEXT");
                log.info("已为xianyu_goods_auto_delivery_config表添加api_delivery_template列");
            }
        } catch (Exception e) {
            log.warn("添加API发货扩展列时出现异常: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void createMediaLibraryTable() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS xianyu_media_library (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "xianyu_account_id INTEGER NOT NULL, " +
                    "file_name VARCHAR(255), " +
                    "media_url TEXT NOT NULL, " +
                    "file_size INTEGER, " +
                    "created_time VARCHAR(30) DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now', '+8 hours'))" +
                    ")");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_media_library_account_id ON xianyu_media_library(xianyu_account_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_media_library_created_time ON xianyu_media_library(created_time)");
            log.info("媒体库表结构检查完成");
        } catch (Exception e) {
            log.warn("创建媒体库表时出现异常: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void ensureChatMessageHotPathIndexes() {
        try {
            String checkTableSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='xianyu_chat_message'";
            List<Map<String, Object>> tableExists = jdbcTemplate.queryForList(checkTableSql);
            if (tableExists.isEmpty()) {
                return;
            }

            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_message_account_time ON xianyu_chat_message(xianyu_account_id, message_time DESC)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_message_sid_time ON xianyu_chat_message(s_id, message_time DESC)");
            log.info("聊天消息热点索引检查完成");
        } catch (Exception e) {
            log.warn("创建聊天消息热点索引时出现异常: {}", e.getMessage());
        }
    }
}
