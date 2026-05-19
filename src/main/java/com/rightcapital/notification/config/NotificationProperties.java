package com.rightcapital.notification.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(RabbitMq rabbitmq, Delivery delivery) {

    public record RabbitMq(String exchange, String queue, String routingKey) {
    }

    public record Delivery(int maxAttempts, Duration requestTimeout, int outboxBatchSize, List<Duration> retryDelays) {

        public Duration delayForAttempt(int attemptCount) {
            int index = Math.max(0, Math.min(attemptCount - 1, retryDelays.size() - 1));
            return retryDelays.get(index);
        }
    }
}
