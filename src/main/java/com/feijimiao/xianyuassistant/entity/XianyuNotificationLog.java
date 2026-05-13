package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 通知日志实体
 */
@Data
@TableName("xianyu_notification_log")
public class XianyuNotificationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channel;

    private String eventType;

    private String title;

    private String content;

    private Integer status;

    private String errorMessage;

    private String createTime;
}
