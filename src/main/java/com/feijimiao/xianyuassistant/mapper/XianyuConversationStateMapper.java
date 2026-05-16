package com.feijimiao.xianyuassistant.mapper;

import com.feijimiao.xianyuassistant.entity.XianyuConversationState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 在线会话状态 Mapper。
 */
@Mapper
public interface XianyuConversationStateMapper {

    @Insert("""
            INSERT INTO xianyu_conversation_state (
                xianyu_account_id, s_id, read_status, read_message_id, read_timestamp, read_receipt
            ) VALUES (
                #{accountId}, #{sId}, 1, #{readMessageId}, #{readTimestamp}, #{readReceipt}
            )
            ON CONFLICT(xianyu_account_id, s_id) DO UPDATE SET
                read_status = CASE
                    WHEN read_status = 0 AND (
                        CASE
                            WHEN read_timestamp > 10000000000000000 THEN read_timestamp / 1000000
                            WHEN read_timestamp > 10000000000000 THEN read_timestamp / 1000
                            WHEN read_timestamp > 0 AND read_timestamp < 100000000000 THEN read_timestamp * 1000
                            ELSE read_timestamp
                        END
                    ) > #{readTimestamp} THEN 0
                    ELSE 1
                END,
                read_message_id = excluded.read_message_id,
                read_timestamp = CASE
                    WHEN read_status = 0 AND (
                        CASE
                            WHEN read_timestamp > 10000000000000000 THEN read_timestamp / 1000000
                            WHEN read_timestamp > 10000000000000 THEN read_timestamp / 1000
                            WHEN read_timestamp > 0 AND read_timestamp < 100000000000 THEN read_timestamp * 1000
                            ELSE read_timestamp
                        END
                    ) > #{readTimestamp} THEN read_timestamp
                    WHEN (
                        CASE
                            WHEN read_timestamp > 10000000000000000 THEN read_timestamp / 1000000
                            WHEN read_timestamp > 10000000000000 THEN read_timestamp / 1000
                            WHEN read_timestamp > 0 AND read_timestamp < 100000000000 THEN read_timestamp * 1000
                            ELSE read_timestamp
                        END
                    ) > #{readTimestamp} THEN read_timestamp
                    ELSE excluded.read_timestamp
                END,
                read_receipt = excluded.read_receipt,
                update_time = datetime('now', 'localtime')
            """)
    int upsertReadReceipt(@Param("accountId") Long accountId,
                          @Param("sId") String sId,
                          @Param("readMessageId") String readMessageId,
                          @Param("readTimestamp") Long readTimestamp,
                          @Param("readReceipt") String readReceipt);

    @Insert("""
            INSERT INTO xianyu_conversation_state (
                xianyu_account_id, s_id, read_status, read_timestamp
            ) VALUES (
                #{accountId}, #{sId}, 0, #{messageTime}
            )
            ON CONFLICT(xianyu_account_id, s_id) DO UPDATE SET
                read_status = CASE
                    WHEN read_timestamp IS NULL OR (
                        CASE
                            WHEN read_timestamp > 10000000000000000 THEN read_timestamp / 1000000
                            WHEN read_timestamp > 10000000000000 THEN read_timestamp / 1000
                            WHEN read_timestamp > 0 AND read_timestamp < 100000000000 THEN read_timestamp * 1000
                            ELSE read_timestamp
                        END
                    ) < #{messageTime} THEN 0
                    ELSE read_status
                END,
                read_timestamp = CASE
                    WHEN read_timestamp IS NULL OR (
                        CASE
                            WHEN read_timestamp > 10000000000000000 THEN read_timestamp / 1000000
                            WHEN read_timestamp > 10000000000000 THEN read_timestamp / 1000
                            WHEN read_timestamp > 0 AND read_timestamp < 100000000000 THEN read_timestamp * 1000
                            ELSE read_timestamp
                        END
                    ) < #{messageTime} THEN #{messageTime}
                    ELSE read_timestamp
                END,
                update_time = datetime('now', 'localtime')
            """)
    int markOutgoingUnread(@Param("accountId") Long accountId,
                           @Param("sId") String sId,
                           @Param("messageTime") Long messageTime);

    @Select("""
            <script>
            SELECT * FROM xianyu_conversation_state
            WHERE xianyu_account_id = #{accountId}
              AND s_id IN
              <foreach collection="sIds" item="sId" open="(" separator="," close=")">
                #{sId}
              </foreach>
            </script>
            """)
    List<XianyuConversationState> findByAccountIdAndSIds(@Param("accountId") Long accountId,
                                                         @Param("sIds") List<String> sIds);
}
