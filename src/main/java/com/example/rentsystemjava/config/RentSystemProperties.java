package com.example.rentsystemjava.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * rent-system.* 配置项绑定（application.yml / application-local.yml / 环境变量）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rent-system")
public class RentSystemProperties {

    private Security security = new Security();
    private Notify notify = new Notify();
    private Init init = new Init();

    @Data
    public static class Security {
        /** SendKey 加密与 HMAC 的统一口令；生产用环境变量 RENT_SECRET */
        private String secret;
    }

    @Data
    public static class Notify {
        /** true = 不真实调用通道，直接记 success（开发调试用） */
        private boolean mock;
        /** 企业微信机器人 webhook，可选降级通道 */
        private String wecomWebhook;
        /** 通道调用超时（秒） */
        private int timeoutSeconds = 10;
    }

    @Data
    public static class Init {
        /** 启动时初始化成员（演示账号，可提交版本库） */
        private List<MemberSeed> members = new ArrayList<>();
        /** 本地/生产真实账号（application-local.yml 或环境变量，不入版本库） */
        private List<MemberSeed> extraMembers = new ArrayList<>();
    }

    @Data
    public static class MemberSeed {
        private String username;
        private String sendKey;
        private String phone;
        /** 每日推送时间 HH:mm，默认 09:00 */
        private String digestSendTime;
    }
}
