package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 登录Token实体类
 * @author IAMLZY
 * @date 2026/4/22
 */
@Data
@TableName("sys_login_token")
public class SysLoginToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户ID */
    private Long userId;

    /** JWT Token */
    private String token;

    /** 设备标识 */
    private String deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 浏览器名称 */
    private String browserName;

    /** 操作系统名称 */
    private String osName;

    /** User-Agent */
    private String userAgent;

    /** 登录IP */
    private String loginIp;

    /** 过期时间 */
    private String expireTime;

    /** 最后活跃时间 */
    private String lastActiveTime;

    /** 状态：1=有效，0=已退出，-1=已踢出 */
    private Integer status;

    /** 创建时间 */
    private String createdTime;

    /** 更新时间 */
    private String updatedTime;
}
