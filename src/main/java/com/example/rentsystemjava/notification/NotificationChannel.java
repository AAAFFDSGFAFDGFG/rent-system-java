package com.example.rentsystemjava.notification;

import com.example.rentsystemjava.domain.enums.ChannelCode;

/**
 * 通知通道策略接口：默认 Server酱，失败降级企业微信机器人（未配置则记 failed 待补发）。
 */
public interface NotificationChannel {

    ChannelCode code();

    /** 发送；成功返回 success=true。实现内不得抛出未捕获异常（统一转失败结果）。 */
    ChannelResult send(String recipient, String title, String content);

    /** 通道调用结果（message 不得含 SendKey） */
    record ChannelResult(boolean success, String message) {

        public static ChannelResult ok() {
            return new ChannelResult(true, "ok");
        }

        public static ChannelResult fail(String message) {
            return new ChannelResult(false, message);
        }
    }
}
