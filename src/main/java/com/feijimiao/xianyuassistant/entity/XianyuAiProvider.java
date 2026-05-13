package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("xianyu_ai_provider")
public class XianyuAiProvider {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String apiKey;

    private String baseUrl;

    private String model;

    private Integer isActive;

    private Integer enabled;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
