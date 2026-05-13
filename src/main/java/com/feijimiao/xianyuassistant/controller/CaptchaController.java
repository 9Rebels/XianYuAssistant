package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.annotation.NoAuth;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.CaptchaDebugImageService;
import com.feijimiao.xianyuassistant.service.CaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 滑块验证控制器，只保留 headless 自动滑块检测入口。
 */
@Slf4j
@RestController
@RequestMapping("/api/captcha")
@CrossOrigin(origins = "*")
@NoAuth
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    @Autowired
    private CaptchaDebugImageService captchaDebugImageService;

    /**
     * 检测并尝试自动处理滑块验证。
     */
    @PostMapping("/detect")
    public ResultObject<Map<String, Object>> detectCaptcha(@RequestBody Map<String, Object> reqBody) {
        try {
            Long accountId = Long.valueOf(reqBody.get("xianyuAccountId").toString());
            String targetUrl = reqBody.get("targetUrl").toString();
            Boolean autoVerify = reqBody.containsKey("autoVerify") ? (Boolean) reqBody.get("autoVerify") : true;

            log.info("检测滑块验证请求: accountId={}, url={}", accountId, targetUrl);

            XianyuCookie cookie = cookieMapper.selectByAccountId(accountId);
            if (cookie == null || cookie.getCookieText() == null) {
                return ResultObject.failed("账号Cookie不存在");
            }

            CaptchaService.CaptchaResult result = captchaService.handleCaptcha(
                    accountId, cookie.getCookieText(), targetUrl, autoVerify);

            Map<String, Object> data = new HashMap<>();
            data.put("needCaptcha", result.isNeedCaptcha());
            data.put("autoVerifySuccess", result.isAutoVerifySuccess());
            data.put("needManual", false);
            data.put("manualVerifyUrl", null);
            data.put("captchaUrl", null);
            data.put("message", result.getMessage());

            return ResultObject.success(data);
        } catch (Exception e) {
            log.error("检测滑块验证失败", e);
            return ResultObject.failed("检测失败: " + e.getMessage());
        }
    }

    /**
     * 获取账号最近一次验证截图，供前端展示人工扫码/人脸处理页面。
     */
    @GetMapping("/debug-image/latest")
    public ResponseEntity<Resource> latestDebugImage(@RequestParam("xianyuAccountId") Long accountId) {
        Optional<Path> latest = captchaDebugImageService.latest(accountId);
        if (latest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(latest.get()));
    }
}
