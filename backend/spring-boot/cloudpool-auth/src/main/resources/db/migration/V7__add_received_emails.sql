-- V7: Self-hosted Email Inbox for incoming SMTP mail

CREATE TABLE received_emails (
    id UUID PRIMARY KEY,
    from_address VARCHAR(255) NOT NULL,
    to_address VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    body TEXT,
    received_at TIMESTAMP NOT NULL
);
