package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 在线消息会话摘要。
 */
@Data
public class OnlineConversationDTO {

    private String sid;

    private Long xianyuAccountId;

    private String peerUserId;

    private String peerUserName;

    private String peerAvatar;

    private String lastMessage;

    private Integer lastContentType;

    private Long lastMessageTime;

    private String xyGoodsId;

    private String goodsTitle;

    private String goodsCoverPic;

    private String goodsPrice;

    private Integer goodsStatus;

    private String orderId;

    private Integer orderStatus;

    private String orderStatusText;

    private String orderAmountText;

    private Integer autoDeliveryState;

    private String autoDeliveryStateText;

    private String cardTitle;

    private String cardSubtitle;

    private String cardImageUrl;

    private String cardActionText;

    private String cardTag;

    private Integer unreadCount;

    private Integer readStatus;

    private String readStatusText;

    private Long readTimestamp;

    private Integer messageCount;
}
