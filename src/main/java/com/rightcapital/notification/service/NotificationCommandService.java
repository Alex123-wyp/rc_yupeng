package com.rightcapital.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.dto.CreateNotificationRequest;
import com.rightcapital.notification.domain.Notification;
import com.rightcapital.notification.domain.NotificationOutbox;
import com.rightcapital.notification.repository.NotificationOutboxRepository;
import com.rightcapital.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationCommandService {

    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;

    public NotificationCommandService(ObjectMapper objectMapper, NotificationRepository notificationRepository,
            NotificationOutboxRepository outboxRepository) {
        this.objectMapper = objectMapper;
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public UUID create(CreateNotificationRequest request) {
        validateTargetUrl(request.targetUrl());
        UUID notificationId = UUID.randomUUID();
        Notification notification = new Notification(
                notificationId,
                request.targetUrl(),
                request.method(),
                toJson(request.safeHeaders()),
                request.body(),
                request.idempotencyKey());

        notificationRepository.save(notification);
        outboxRepository.save(new NotificationOutbox(UUID.randomUUID(), notificationId));
        return notificationId;
    }

    @Transactional(readOnly = true)
    public Notification get(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id));
    }

    private void validateTargetUrl(String targetUrl) {
        URI uri = URI.create(targetUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("targetUrl must use http or https");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("targetUrl must include a host");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid headers", exception);
        }
    }
}
