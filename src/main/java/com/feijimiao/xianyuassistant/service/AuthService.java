package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.service.bo.*;
import java.util.List;

/**
 * 认证服务接口
 * @author IAMLZY
 * @date 2026/4/22
 */
public interface AuthService {

    /**
     * 检查是否有用户存在（决定显示登录还是注册）
     */
    CheckUserExistsRespBO checkUserExists();

    /**
     * 注册
     */
    LoginRespBO register(RegisterReqBO reqBO);

    /**
     * 登录
     */
    LoginRespBO login(LoginReqBO reqBO);

    /**
     * 验证Token是否有效（在数据库中且未过期）
     */
    boolean isTokenValid(String token);

    /**
     * 刷新Token最后活跃时间
     */
    void touchToken(String token);

    /**
     * 检查IP登录错误次数限制
     * @return true=允许登录 false=超过限制
     */
    boolean checkLoginAttempt(String ip);

    /**
     * 记录登录失败
     */
    void recordLoginFailure(String ip);

    /**
     * 清除登录失败记录
     */
    void clearLoginFailure(String ip);

    /**
     * 获取当前用户信息
     */
    com.feijimiao.xianyuassistant.entity.SysUser getCurrentUser(Long userId);

    /**
     * 修改密码
     */
    void changePassword(ChangePasswordReqBO reqBO);

    /**
     * 查询登录设备
     */
    List<LoginDeviceBO> listLoginDevices(Long userId, String currentToken);

    /**
     * 踢出指定登录设备
     */
    void kickLoginDevice(Long userId, Long tokenId, String currentToken);

    /**
     * 退出登录
     */
    void logout(String token);
}
