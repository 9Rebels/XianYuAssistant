package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 闲鱼商品信息实体类
 */
@Data
@TableName("xianyu_goods")
public class XianyuGoodsInfo {
    
    /**
     * 主键ID（雪花ID）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 闲鱼商品ID
     */
    private String xyGoodId;
    
    /**
     * 商品标题
     */
    private String title;
    
    /**
     * 封面图片URL
     */
    private String coverPic;
    
    /**
     * 商品详情图片（JSON数组）
     */
    private String infoPic;
    
    /**
     * 商品详情信息（预留字段）
     */
    private String detailInfo;
    
    /**
     * 商品详情页URL
     */
    private String detailUrl;
    
    /**
     * 关联的闲鱼账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * 商品价格
     */
    private String soldPrice;

    /**
     * 闲鱼库存
     */
    private Integer quantity;

    /**
     * 曝光次数
     */
    private Integer exposureCount;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 想要人数
     */
    private Integer wantCount;
    
    /**
     * 本地商品状态 0:在售 1:已下架 2:已售出 3:闲鱼已删除
     */
    private Integer status;

    /**
     * 闲鱼客户端列表顺序，数值越小越靠前
     */
    private Integer sortOrder;
    
    /**
     * 创建时间（SQLite存储为TEXT）
     */
    private String createdTime;
    
    /**
     * 更新时间（SQLite存储为TEXT）
     */
    private String updatedTime;
}
