package com.rightcapital.notification.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder, NotificationProperties properties) {
        return builder
                .setConnectTimeout(properties.delivery().requestTimeout())
                .setReadTimeout(properties.delivery().requestTimeout())
                .build();
    }
}
