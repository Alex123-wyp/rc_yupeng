package com.rightcapital.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")

/**
 * 对应Notification表格
 */
public class Notification {

    @Id
    private UUID id;

    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false)
    private HttpMethodType httpMethod;

    @Column(name = "headers_json", nullable = false)
    private String headersJson;

    @Column(name = "body")
    private String body;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Notification() {
    }

    public Notification(UUID id, String targetUrl, HttpMethodType httpMethod, String headersJson, String body,
            String idempotencyKey) {
        this.id = id;
        this.targetUrl = targetUrl;
        this.httpMethod = httpMethod;
        this.headersJson = headersJson;
        this.body = body;
        this.idempotencyKey = idempotencyKey;
        this.status = NotificationStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = OffsetDateTime.now();
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void markSucceeded() {
        this.status = NotificationStatus.SUCCEEDED;
        this.lastError = null;
        this.nextAttemptAt = null;
    }

    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.lastError = truncate(reason);
        this.nextAttemptAt = null;
    }

    public void markDead(String reason) {
        this.status = NotificationStatus.DEAD;
        this.lastError = truncate(reason);
        this.nextAttemptAt = null;
    }

    public void scheduleRetry(OffsetDateTime nextAttemptAt, String reason) {
        this.status = NotificationStatus.RETRY_SCHEDULED;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = truncate(reason);
    }

    public void markPendingForRetry() {
        this.status = NotificationStatus.PENDING;
        this.nextAttemptAt = null;
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 2000) {
            return value;
        }
        return value.substring(0, 2000);
    }

    public UUID getId() {
        return id;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public HttpMethodType getHttpMethod() {
        return httpMethod;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public String getBody() {
        return body;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
