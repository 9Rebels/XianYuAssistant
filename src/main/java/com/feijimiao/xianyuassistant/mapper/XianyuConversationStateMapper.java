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
                read_status = 1,
                read_message_id = excluded.read_message_id,
                read_timestamp = excluded.read_timestamp,
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
                    WHEN read_timestamp IS NULL OR read_timestamp < #{messageTime} THEN 0
                    ELSE read_status
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
