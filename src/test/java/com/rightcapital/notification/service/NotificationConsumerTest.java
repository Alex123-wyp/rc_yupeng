package com.rightcapital.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.config.NotificationProperties;
import com.rightcapital.notification.domain.HttpMethodType;
import com.rightcapital.notification.domain.Notification;
import com.rightcapital.notification.domain.NotificationStatus;
import com.rightcapital.notification.repository.NotificationRepository;
import com.rightcapital.notification.worker.NotificationConsumer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

class NotificationConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationProperties properties = new NotificationProperties(
            new NotificationProperties.RabbitMq("exchange", "queue", "routing"),
            new NotificationProperties.Delivery(
                    5,
                    Duration.ofSeconds(5),
                    50,
                    List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15))));
    private final NotificationRepository repository = Mockito.mock(NotificationRepository.class);
    private final RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
    private final NotificationConsumer consumer =
            new NotificationConsumer(objectMapper, properties, repository, restTemplate);

    @Test
    void marksNotificationSucceededWhenExternalApiReturns2xx() {
        Notification notification = notification();
        when(repository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(restTemplate.exchange(
                eq(notification.getTargetUrl()),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        consumer.consume(new NotificationMessage(notification.getId()));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SUCCEEDED);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getLastError()).isNull();
    }

    @Test
    void schedulesRetryForRetryableFailure() {
        Notification notification = notification();
        when(repository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(restTemplate.exchange(
                eq(notification.getTargetUrl()),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        consumer.consume(new NotificationMessage(notification.getId()));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRY_SCHEDULED);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getNextAttemptAt()).isNotNull();
        assertThat(notification.getLastError()).contains("HTTP 502");
    }

    @Test
    void marksDeadAfterMaxAttempts() {
        Notification notification = notification();
        for (int i = 0; i < 4; i++) {
            notification.incrementAttemptCount();
        }
        when(repository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(restTemplate.exchange(
                eq(notification.getTargetUrl()),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        consumer.consume(new NotificationMessage(notification.getId()));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DEAD);
        assertThat(notification.getAttemptCount()).isEqualTo(5);
        assertThat(notification.getLastError()).contains("HTTP 503");
    }

    private Notification notification() {
        return new Notification(
                UUID.randomUUID(),
                "https://vendor.example/notify",
                HttpMethodType.POST,
                "{\"Content-Type\":\"application/json\"}",
                "{\"event\":\"registered\"}",
                "abc-123");
    }
}
