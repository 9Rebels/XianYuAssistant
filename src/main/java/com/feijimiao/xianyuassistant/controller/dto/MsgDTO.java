package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息DTO
 */
@Data
public class MsgDTO {
    
    /**
     * 消息ID
     */
    private Long id;
    
    /**
     * 消息聊天框id
     */
    private String sId;
    
    /**
     * 消息类别（1-用户消息，2-图片，32-已付款待发货，其他）
     */
    private Integer contentType;
    
    /**
     * 消息内容
     */
    private String msgContent;

    /**
     * 完整消息体JSON，用于前端解析卡片、图片等富消息
     */
    private String completeMsg;

    /**
     * 结构化消息类型：text/image/audio/card/system_tip/trade_card
     */
    private String messageKind;

    /**
     * 用于列表和气泡展示的净化文本
     */
    private String displayText;

    /**
     * 图片消息地址列表
     */
    private List<String> imageUrls = new ArrayList<>();

    /**
     * 语音等媒体消息信息
     */
    private MediaDTO media;

    /**
     * 商品/交易卡片结构化信息
     */
    private CardDTO card;
    
    /**
     * 闲鱼商品ID
     */
    private String xyGoodsId;
    
    /**
     * 消息链接
     */
    private String reminderUrl;
    
    /**
     * 发送者用户名称
     */
    private String senderUserName;
    
    /**
     * 发送者用户id
     */
    private String senderUserId;
    
    /**
     * 消息时间戳（毫秒）
     */
    private Long messageTime;

    @Data
    public static class MediaDTO {

        private String type;

        private String url;

        private Long durationMs;

        private Integer width;

        private Integer height;
    }

    @Data
    public static class CardDTO {

        private String title;

        private String subtitle;

        private String actionText;

        private String imageUrl;

        private String url;

        private String tag;

        private String orderId;

        private String taskId;
    }
}

