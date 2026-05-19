CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    target_url TEXT NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    headers_json TEXT NOT NULL,
    body TEXT,
    idempotency_key VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notifications_status_next_attempt
    ON notifications(status, next_attempt_at);

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notifications(id),
    status VARCHAR(32) NOT NULL,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_notification_outbox_status_created
    ON notification_outbox(status, created_at);
