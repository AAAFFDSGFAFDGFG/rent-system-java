package com.example.rentsystemjava.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 提供应用级 Clock Bean（Spring Boot 4 不再默认注册 Clock）。
 * FinanceService 注入它以计算「今日」；测试可传入 Clock.fixed 做确定性断言。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
