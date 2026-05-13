package com.feijimiao.xianyuassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "slider")
public class SliderProperties {

    private final Retry retry = new Retry();
    private final Proxy proxy = new Proxy();
    private final Stealth stealth = new Stealth();
    private final FailureCapture failureCapture = new FailureCapture();

    @Data
    public static class Retry {
        /**
         * 外层 reload 兜底循环次数；每次循环内部仍走 SliderAutoVerifyService 的 3 次拖动重试。
         * 总尝试次数 = innerAttempts * outerReloadAttempts，对齐参考项目 5*3 量级。
         */
        private int outerReloadAttempts = 3;
        /**
         * 外层 reload 之间的等待毫秒区间下界。
         */
        private long outerReloadDelayMinMs = 2000L;
        /**
         * 外层 reload 之间的等待毫秒区间上界。
         */
        private long outerReloadDelayMaxMs = 4000L;
    }

    @Data
    public static class Proxy {
        private boolean enabled = false;
        /**
         * Playwright server 形如 http://host:port 或 socks5://host:port。
         * 若 type+host+port 都填写，将自动拼接，无需另行指定 server。
         */
        private String server = "";
        /**
         * http / https / socks5
         */
        private String type = "http";
        private String host = "";
        private int port = 0;
        private String username = "";
        private String password = "";
        /**
         * 绕过列表，逗号分隔，如 "*.local,127.0.0.1"
         */
        private String bypass = "";
    }

    @Data
    public static class Stealth {
        /**
         * off / lite / full。off=不注入；lite=仅核心 webdriver/plugins/uaData；full=完整指纹伪装。
         */
        private String mode = "full";
    }

    @Data
    public static class FailureCapture {
        private boolean enabled = true;
        /**
         * 截图根目录；相对路径将基于 user.dir 解析。
         */
        private String directory = "dbdata/captcha-debug";
        /**
         * 单账号保留的最近截图数；超出后旧文件按时间淘汰，0 表示不淘汰。
         */
        private int retainPerAccount = 30;
    }
}
