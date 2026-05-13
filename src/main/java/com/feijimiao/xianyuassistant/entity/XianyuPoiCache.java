package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 发布地图点位缓存
 */
@Data
@TableName("xianyu_poi_cache")
public class XianyuPoiCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long xianyuAccountId;

    private Integer divisionId;

    private String prov;

    private String city;

    private String area;

    private String poiId;

    private String poiName;

    private String gps;

    private String latitude;

    private String longitude;

    private String address;

    private String source;

    private Integer isDefault;

    private String createdTime;

    private String updatedTime;
}
