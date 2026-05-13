package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.annotation.NoAuth;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 人工验证码页已下线，显式返回 404，避免落到 SPA fallback。
 */
@Controller
@NoAuth
public class DeprecatedCaptchaPageController {

    @GetMapping("/captcha-verify.html")
    public ResponseEntity<Void> captchaVerifyPage() {
        return ResponseEntity.notFound().build();
    }
}
