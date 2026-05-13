package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("xianyu_item_polish_task")
public class XianyuItemPolishTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long xianyuAccountId;

    private Integer enabled;

    private Integer runHour;

    private Integer randomDelayMaxMinutes;

    private String nextRunTime;

    private String lastRunTime;

    private String lastResult;

    private String createTime;

    private String updateTime;
}
