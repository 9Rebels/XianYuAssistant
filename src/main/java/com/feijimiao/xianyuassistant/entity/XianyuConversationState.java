package com.feijimiao.xianyuassistant.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线会话状态。
 */
@Data
public class XianyuConversationState {

    private Long id;

    private Long xianyuAccountId;

    private String sId;

    private Integer readStatus;

    private String readMessageId;

    private Long readTimestamp;

    private String readReceipt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
