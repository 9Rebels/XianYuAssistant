package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;



/**
 * 闲鱼账号实体类
 */
@Data
@TableName("xianyu_account")
public class XianyuAccount {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 闲鱼账号备注
     */
    private String accountNote;
    
    /**
     * UNB标识
     */
    private String unb;
    
    /**
     * 设备ID（UUID格式-用户ID，用于WebSocket连接）
     * 格式: XXXXXXXX-XXXX-4XXX-XXXX-XXXXXXXXXXXX-用户ID
     * 例如: ED4CBA2C-5DA0-4154-A902-BF5CB52409E2-3888777108
     */
    private String deviceId;

    /**
     * 闲鱼昵称
     */
    private String displayName;

    /**
     * 闲鱼头像
     */
    private String avatar;

    /**
     * IP属地
     */
    private String ipLocation;

    /**
     * 个人简介
     */
    private String introduction;

    /**
     * 粉丝数
     */
    private String followers;

    /**
     * 关注数
     */
    private String following;

    /**
     * 卖出数
     */
    private Integer soldCount;

    /**
     * 买入数
     */
    private Integer purchaseCount;

    /**
     * 好评率
     */
    private String praiseRatio;

    /**
     * 评价数
     */
    private Integer reviewNum;

    /**
     * 卖家等级
     */
    private String sellerLevel;

    /**
     * 是否鱼小铺
     */
    private Boolean fishShopUser;

    /**
     * 鱼小铺等级
     */
    private String fishShopLevel;

    /**
     * 鱼小铺分数
     */
    private Integer fishShopScore;

    /**
     * 个人资料同步时间
     */
    private String profileUpdatedTime;

    /**
     * 个人资料刷新尝试时间（成功/失败都会记录，用于防重复请求）
     */
    private String profileRefreshAttemptTime;

    private String proxyType;

    private String proxyHost;

    private Integer proxyPort;

    private String proxyUsername;

    private String proxyPassword;

    private String loginUsername;

    private String loginPassword;

    /**
     * 账号状态 -2:需要人机验证 -1:需要手机号验证 1:正常
     */
    private Integer status;
    
    /**
     * 创建时间（SQLite存储为TEXT）
     */
    private String createdTime;
    
    /**
     * 更新时间（SQLite存储为TEXT）
     */
    private String updatedTime;

    /**
     * 状态变更原因。灰度字段：未执行DDL前不参与默认查询。
     */
    @TableField(select = false)
    private String stateReason;

    /**
     * 状态变更时间。灰度字段：未执行DDL前不参与默认查询。
     */
    @TableField(select = false)
    private String stateUpdatedTime;
}
