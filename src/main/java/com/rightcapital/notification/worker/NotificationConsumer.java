package com.rightcapital.notification.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.config.NotificationProperties;
import com.rightcapital.notification.domain.Notification;
import com.rightcapital.notification.repository.NotificationRepository;
import com.rightcapital.notification.service.NotificationMessage;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class NotificationConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationProperties properties;
    private final NotificationRepository notificationRepository;
    private final RestTemplate restTemplate;

    public NotificationConsumer(ObjectMapper objectMapper, NotificationProperties properties,
            NotificationRepository notificationRepository, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.notificationRepository = notificationRepository;
        this.restTemplate = restTemplate;
    }

    @RabbitListener(queues = "${notification.rabbitmq.queue}")
    @Transactional
    public void consume(NotificationMessage message) {
        Notification notification = notificationRepository.findById(message.notificationId())
                .orElse(null);
        if (notification == null || notification.getStatus().name().equals("SUCCEEDED")) {
            return;
        }

        notification.incrementAttemptCount();
        try {

            //发送请求，接收返回结果
            ResponseEntity<String> response = restTemplate.exchange(
                    notification.getTargetUrl(),
                    HttpMethod.valueOf(notification.getHttpMethod().name()),
                    new HttpEntity<>(notification.getBody(), headers(notification)),
                    String.class);

            /**
             * 如果调用API成功，则标记成功； 如果返回码是429或者大于等于500，则将参数传递给retryOrDead()函数中判断；
             * 否则直接标记为failed，因为大多数4xx代表请求本身有问题，通常重试也不会成功。但429是限流，所以可以重试
             */
            if (response.getStatusCode().is2xxSuccessful()) {
                notification.markSucceeded();
            } else if (isRetryableStatus(response.getStatusCode().value())) {
                retryOrDead(notification, "HTTP " + response.getStatusCode().value());
            } else {
                notification.markFailed("Non-retryable HTTP " + response.getStatusCode().value());
            }
        } catch (HttpStatusCodeException exception) {
            if (isRetryableStatus(exception.getStatusCode().value())) {
                retryOrDead(notification, "HTTP " + exception.getStatusCode().value());
            } else {
                notification.markFailed("Non-retryable HTTP " + exception.getStatusCode().value());
            }
        } catch (RestClientException exception) {
            retryOrDead(notification, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    // 生成请求头
    private HttpHeaders headers(Notification notification) {
        HttpHeaders httpHeaders = new HttpHeaders();
        try {
            Map<String, String> headers = objectMapper.readValue(
                    notification.getHeadersJson(),
                    new TypeReference<>() {
                    });
            headers.forEach(httpHeaders::add);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Stored headers are not valid JSON", exception);
        }
        if (notification.getIdempotencyKey() != null && !notification.getIdempotencyKey().isBlank()) {
            httpHeaders.add("Idempotency-Key", notification.getIdempotencyKey());
        }
        return httpHeaders;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void retryOrDead(Notification notification, String reason) {
        //如果超过最大重试次数，则直接标记为死亡（死信队列）
        if (notification.getAttemptCount() >= properties.delivery().maxAttempts()) {
            notification.markDead(reason);
            return;
        }
        //如果没有超过最大可重试次数，把该notification标记为稍后重试然后记录下一次重试时间
        notification.scheduleRetry(
                OffsetDateTime.now().plus(properties.delivery().delayForAttempt(notification.getAttemptCount())),
                reason);
    }
}
