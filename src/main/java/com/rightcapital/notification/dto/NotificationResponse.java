package com.rightcapital.notification.dto;

import com.rightcapital.notification.domain.HttpMethodType;
import com.rightcapital.notification.domain.Notification;
import com.rightcapital.notification.domain.NotificationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String targetUrl,
        HttpMethodType method,
        NotificationStatus status,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTargetUrl(),
                notification.getHttpMethod(),
                notification.getStatus(),
                notification.getAttemptCount(),
                notification.getNextAttemptAt(),
                notification.getLastError(),
                notification.getCreatedAt(),
                notification.getUpdatedAt());
    }
}
