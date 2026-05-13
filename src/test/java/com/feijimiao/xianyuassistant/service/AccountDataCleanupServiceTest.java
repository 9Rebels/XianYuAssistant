package com.feijimiao.xianyuassistant.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDataCleanupServiceTest {

    @Test
    void deleteAccountAndRelatedDataCleansAccountOwnedTablesAndKamiChildren() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AccountDataCleanupService service = new AccountDataCleanupService(jdbcTemplate);
        when(jdbcTemplate.update(eq("DELETE FROM xianyu_kami_usage_record WHERE xianyu_account_id = ?"), eq(4L)))
                .thenReturn(3);
        when(jdbcTemplate.update(eq("""
                DELETE FROM xianyu_kami_item
                WHERE kami_config_id IN (
                    SELECT id FROM xianyu_kami_config WHERE xianyu_account_id = ?
                )
                """), eq(4L))).thenReturn(8);
        when(jdbcTemplate.update(eq("DELETE FROM xianyu_account WHERE id = ?"), eq(4L))).thenReturn(1);

        Map<String, Integer> deleted = service.deleteAccountAndRelatedData(4L);

        assertEquals(3, deleted.get("xianyu_kami_usage_record"));
        assertEquals(8, deleted.get("xianyu_kami_item"));
        assertEquals(1, deleted.get("xianyu_account"));
        verify(jdbcTemplate).update("DELETE FROM xianyu_conversation_state WHERE xianyu_account_id = ?", 4L);
        verify(jdbcTemplate).update("DELETE FROM xianyu_auto_reply_rule WHERE xianyu_account_id = ?", 4L);
        verify(jdbcTemplate).update("DELETE FROM xianyu_media_library WHERE xianyu_account_id = ?", 4L);
        verify(jdbcTemplate).update("DELETE FROM xianyu_poi_cache WHERE xianyu_account_id = ?", 4L);
        verify(jdbcTemplate).update("DELETE FROM xianyu_publish_schedule WHERE xianyu_account_id = ?", 4L);
        verify(jdbcTemplate).update("DELETE FROM xianyu_order WHERE xianyu_account_id = ?", 4L);
    }
}
