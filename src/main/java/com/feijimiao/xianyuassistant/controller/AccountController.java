package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.controller.dto.AccountReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AddAccountRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.DeleteAccountReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.DeleteAccountRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.GetAccountDetailReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.GetAccountDetailRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.GetAccountListRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.LoginCredentialRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.ManualAddAccountReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.RefreshAccountProfileReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.RefreshAccountProfileRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.UpdateAccountReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.UpdateAccountRespDTO;
import com.feijimiao.xianyuassistant.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 账号管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/account")
@CrossOrigin(origins = "*")
public class AccountController {

    @Autowired
    private XianyuAccountMapper accountMapper;
    
    @Autowired
    private AccountService accountService;

    /**
     * 获取账号列表
     */
    @PostMapping("/list")
    public ResultObject<GetAccountListRespDTO> getAccountList() {
        try {
            List<XianyuAccount> accounts = accountMapper.selectList(null);
            for (XianyuAccount account : accounts) {
                sanitizeSensitiveFields(account);
            }
            GetAccountListRespDTO respDTO = new GetAccountListRespDTO();
            respDTO.setAccounts(accounts);
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取账号列表失败", e);
            return ResultObject.failed("获取账号列表失败: " + e.getMessage());
        }
    }

    /**
     * 添加账号
     */
    @PostMapping("/add")
    public ResultObject<AddAccountRespDTO> addAccount(@RequestBody AccountReqDTO reqDTO) {
        try {
            log.info("添加账号请求: accountNote={}", reqDTO.getAccountNote());
            
            if (reqDTO.getCookie() == null || reqDTO.getCookie().isEmpty()) {
                return ResultObject.failed("Cookie不能为空");
            }
            
            Long accountId = accountService.saveAccountAndCookie(
                    reqDTO.getAccountNote(),
                    reqDTO.getUnb(),
                    reqDTO.getCookie()
            );
            
            AddAccountRespDTO respDTO = new AddAccountRespDTO();
            respDTO.setAccountId(accountId);
            respDTO.setMessage("添加成功");
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("添加账号失败", e);
            return ResultObject.failed("添加账号失败: " + e.getMessage());
        }
    }

    /**
     * 手动添加账号
     */
    @PostMapping("/manualAdd")
    public ResultObject<AddAccountRespDTO> manualAddAccount(@RequestBody ManualAddAccountReqDTO reqDTO) {
        try {
            log.info("手动添加账号请求: accountNote={}", reqDTO.getAccountNote());
            
            if (reqDTO.getCookie() == null || reqDTO.getCookie().isEmpty()) {
                return ResultObject.failed("Cookie不能为空");
            }
            
            // 从Cookie中提取unb信息
            String unb = extractUnbFromCookie(reqDTO.getCookie());
            if (unb == null || unb.isEmpty()) {
                return ResultObject.failed("无法从Cookie中提取UNB信息");
            }
            
            // 检查账号是否已存在
            Long existingAccountId = accountService.getAccountIdByUnb(unb);
            if (existingAccountId != null) {
                return ResultObject.failed("账号已存在");
            }
            
            // 保存账号和Cookie信息
            Long accountId = accountService.saveAccountAndCookie(
                    reqDTO.getAccountNote(),
                    unb,
                    reqDTO.getCookie()
            );
            
            AddAccountRespDTO respDTO = new AddAccountRespDTO();
            respDTO.setAccountId(accountId);
            respDTO.setMessage("添加成功");
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("手动添加账号失败", e);
            return ResultObject.failed("添加账号失败: " + e.getMessage());
        }
    }
    
    /**
     * 从Cookie字符串中提取UNB值
     *
     * @param cookie Cookie字符串
     * @return UNB值，如果未找到则返回null
     */
    private String extractUnbFromCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            return null;
        }
        
        // 查找unb=后面的值
        String[] cookieParts = cookie.split(";\\s*");
        for (String part : cookieParts) {
            if (part.startsWith("unb=")) {
                return part.substring(4); // "unb=".length() = 4
            }
        }
        
        return null;
    }

    /**
     * 更新账号
     */
    @PostMapping("/update")
    public ResultObject<UpdateAccountRespDTO> updateAccount(@RequestBody UpdateAccountReqDTO reqDTO) {
        try {
            log.info("更新账号请求: accountId={}", reqDTO.getAccountId());
            
            if (reqDTO.getAccountId() == null) {
                return ResultObject.failed("账号ID不能为空");
            }
            
            XianyuAccount account = accountMapper.selectById(reqDTO.getAccountId());
            if (account == null) {
                return ResultObject.failed("账号不存在");
            }
            
            // 只更新账号备注
            if (reqDTO.getAccountNote() != null) {
                String accountNote = reqDTO.getAccountNote().trim();
                account.setAccountNote(accountNote);
            }

            if (Boolean.TRUE.equals(reqDTO.getUpdateProxy())) {
                account.setProxyType(reqDTO.getProxyType());
                account.setProxyHost(reqDTO.getProxyHost());
                account.setProxyPort(reqDTO.getProxyPort());
                account.setProxyUsername(reqDTO.getProxyUsername());
                account.setProxyPassword(reqDTO.getProxyPassword());
            }

            if (reqDTO.getLoginUsername() != null) {
                account.setLoginUsername(reqDTO.getLoginUsername().trim());
            }
            if (Boolean.TRUE.equals(reqDTO.getClearLoginPassword())) {
                account.setLoginPassword(null);
            } else if (reqDTO.getLoginPassword() != null) {
                account.setLoginPassword(reqDTO.getLoginPassword().trim());
            }
            
            accountMapper.updateById(account);
            
            // 不再更新Cookie和UNB
            
            UpdateAccountRespDTO respDTO = new UpdateAccountRespDTO();
            respDTO.setMessage("更新成功");
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("更新账号失败", e);
            return ResultObject.failed("更新账号失败: " + e.getMessage());
        }
    }

    /**
     * 手动刷新账号资料。
     */
    @PostMapping("/refreshProfile")
    public ResultObject<RefreshAccountProfileRespDTO> refreshAccountProfile(
            @RequestBody RefreshAccountProfileReqDTO reqDTO) {
        try {
            if (reqDTO.getAccountId() == null) {
                return ResultObject.failed("账号ID不能为空");
            }

            XianyuAccount account = accountService.refreshAccountProfileManually(reqDTO.getAccountId());
            sanitizeSensitiveFields(account);
            RefreshAccountProfileRespDTO respDTO = new RefreshAccountProfileRespDTO();
            respDTO.setAccount(account);
            respDTO.setMessage("刷新成功");
            return ResultObject.success(respDTO);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("账号资料1小时内只能刷新一次")) {
                log.warn("刷新账号资料被限流: accountId={}, message={}", reqDTO.getAccountId(), e.getMessage());
                return ResultObject.failed(429, e.getMessage());
            }
            log.warn("刷新账号资料失败: accountId={}, message={}", reqDTO.getAccountId(), e.getMessage());
            return ResultObject.failed(e.getMessage());
        } catch (Exception e) {
            log.error("刷新账号资料失败", e);
            return ResultObject.failed("刷新账号资料失败: " + e.getMessage());
        }
    }

    /**
     * 删除账号
     */
    @PostMapping("/delete")
    public ResultObject<DeleteAccountRespDTO> deleteAccount(@RequestBody DeleteAccountReqDTO reqDTO) {
        try {
            Long id = reqDTO.getAccountId();
            log.info("删除账号请求: accountId={}", id);
            
            XianyuAccount account = accountMapper.selectById(id);
            if (account == null) {
                return ResultObject.failed("账号不存在");
            }
            
            // 删除账号关联的所有数据
            accountService.deleteAccountAndRelatedData(id);
            
            DeleteAccountRespDTO respDTO = new DeleteAccountRespDTO();
            respDTO.setMessage("删除成功");
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("删除账号失败", e);
            return ResultObject.failed("删除账号失败: " + e.getMessage());
        }
    }

    /**
     * 获取账号详情
     */
    @PostMapping("/detail")
    public ResultObject<GetAccountDetailRespDTO> getAccountDetail(@RequestBody GetAccountDetailReqDTO reqDTO) {
        try {
            Long id = reqDTO.getAccountId();
            XianyuAccount account = accountMapper.selectById(id);
            if (account == null) {
                return ResultObject.failed("账号不存在");
            }
            sanitizeSensitiveFields(account);
            GetAccountDetailRespDTO respDTO = new GetAccountDetailRespDTO();
            respDTO.setAccount(account);
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取账号详情失败", e);
            return ResultObject.failed("获取账号详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取账号密码登录凭据。
     */
    @PostMapping("/loginCredential")
    public ResultObject<LoginCredentialRespDTO> getLoginCredential(@RequestBody GetAccountDetailReqDTO reqDTO) {
        try {
            if (reqDTO.getAccountId() == null) {
                return ResultObject.failed("账号ID不能为空");
            }
            XianyuAccount account = accountMapper.selectById(reqDTO.getAccountId());
            if (account == null) {
                return ResultObject.failed("账号不存在");
            }
            LoginCredentialRespDTO respDTO = new LoginCredentialRespDTO();
            respDTO.setAccountId(account.getId());
            respDTO.setLoginUsername(account.getLoginUsername());
            respDTO.setLoginPassword(account.getLoginPassword());
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取登录凭据失败", e);
            return ResultObject.failed("获取登录凭据失败: " + e.getMessage());
        }
    }



    private void sanitizeSensitiveFields(XianyuAccount account) {
        if (account == null) {
            return;
        }
        if (account.getProxyPassword() != null && !account.getProxyPassword().isEmpty()) {
            account.setProxyPassword("***");
        }
        if (account.getLoginPassword() != null && !account.getLoginPassword().isEmpty()) {
            account.setLoginPassword("***");
        }
    }
}
