package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动回复关键词规则
 */
@Data
@TableName("xianyu_auto_reply_rule")
public class XianyuAutoReplyRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long xianyuAccountId;

    private String xyGoodsId;

    private String ruleName;

    private String keywords;

    private Integer matchType;

    private Integer replyType;

    private String replyContent;

    private String imageUrls;

    private Integer priority;

    private Integer enabled;

    private Integer isDefault;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
