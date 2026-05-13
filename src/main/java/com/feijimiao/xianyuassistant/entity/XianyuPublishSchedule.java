package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("xianyu_publish_schedule")
public class XianyuPublishSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long xianyuAccountId;

    private String title;

    private String payloadJson;

    private Integer status;

    private String scheduledTime;

    private String executedTime;

    private String itemId;

    private String failReason;

    private String createdTime;

    private String updatedTime;
}
