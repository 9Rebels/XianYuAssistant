package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 媒体库图片素材
 */
@Data
@TableName("xianyu_media_library")
public class XianyuMediaLibrary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long xianyuAccountId;

    private String fileName;

    private String mediaUrl;

    private Long fileSize;

    private String createdTime;
}
