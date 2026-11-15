package com.example.rentsystemjava.notification;

import com.example.rentsystemjava.config.RentSystemProperties;
import com.example.rentsystemjava.domain.enums.ChannelCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Server酱通道（默认，免费公众号推送到家人个人微信）。
 * POST https://sctapi.ftqq.com/{SENDKEY}.send，表单 title + desp；响应 JSON code==0 判成功。
 * SendKey 只出现在请求 URL，不写入任何日志。
 */
@Component
public class ServerchanChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(ServerchanChannel.class);
    private static final String API = "https://sctapi.ftqq.com/{key}.send";

    private final RestClient restClient;
    private final RentSystemProperties props;

    public ServerchanChannel(RestClient.Builder builder, RentSystemProperties props) {
        this.props = props;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getNotify().getTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(props.getNotify().getTimeoutSeconds()));
        this.restClient = builder.requestFactory(factory).build();
    }

    @Override
    public ChannelCode code() {
        return ChannelCode.SERVERCHAN;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelResult send(String sendKey, String title, String content) {
        // 开发调试开关：不真实调用外网
        if (props.getNotify().isMock()) {
            log.info("[mock] Server酱 推送已模拟成功 title={}", title);
            return ChannelResult.ok();
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("title", title);
            form.add("desp", content);
            Map<String, Object> resp = restClient.post()
                    .uri(API, sendKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object code = resp == null ? null : resp.get("code");
            if (code instanceof Number n && n.intValue() == 0) {
                return ChannelResult.ok();
            }
            Object errmsg = resp == null ? "空响应" : resp.get("message");
            return ChannelResult.fail("Server酱 返回失败: " + errmsg);
        } catch (Exception e) {
            // 脱敏：异常信息可能带 URL（含 SendKey），替换后才能入库/入日志
            return ChannelResult.fail(sanitize("Server酱 调用异常: " + e.getMessage(), sendKey));
        }
    }

    public static String sanitize(String message, String secret) {
        return secret == null || message == null ? message : message.replace(secret, "***");
    }
}
