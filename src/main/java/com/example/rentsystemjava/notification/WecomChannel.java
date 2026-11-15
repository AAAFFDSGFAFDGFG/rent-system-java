package com.example.rentsystemjava.notification;

import com.example.rentsystemjava.config.RentSystemProperties;
import com.example.rentsystemjava.domain.enums.ChannelCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * 企业微信群机器人通道（可选降级备胎；仅企业内部群，不能直达个人微信）。
 * webhook 未配置时直接返回失败（上层记 failed 待补发）。
 */
@Component
public class WecomChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WecomChannel.class);

    private final RestClient restClient;
    private final RentSystemProperties props;

    public WecomChannel(RestClient.Builder builder, RentSystemProperties props) {
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
        return ChannelCode.WECOM;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelResult send(String webhook, String title, String content) {
        if (webhook == null || webhook.isBlank()) {
            return ChannelResult.fail("企业微信机器人未配置");
        }
        try {
            Map<String, Object> resp = restClient.post()
                    .uri(webhook)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("msgtype", "markdown",
                            "markdown", Map.of("content", "**" + title + "**\n" + content)))
                    .retrieve()
                    .body(Map.class);
            Object errno = resp == null ? null : resp.get("errcode");
            if (errno instanceof Number n && n.intValue() == 0) {
                return ChannelResult.ok();
            }
            return ChannelResult.fail("企业微信 返回失败: " + (resp == null ? "空响应" : resp.get("errmsg")));
        } catch (Exception e) {
            return ChannelResult.fail("企业微信 调用异常: " + e.getMessage());
        }
    }
}
