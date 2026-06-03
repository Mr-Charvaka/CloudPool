-- V6: Self-hosted Email Outbox and Request Analytics

CREATE TABLE outbox_emails (
    id UUID PRIMARY KEY,
    to_address VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message TEXT
);

CREATE TABLE analytics_api_logs (
    id UUID PRIMARY KEY,
    project_id UUID,
    request_path VARCHAR(255) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    status_code INT NOT NULL,
    duration_ms BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    ip_address VARCHAR(50),
    user_agent VARCHAR(255)
);
