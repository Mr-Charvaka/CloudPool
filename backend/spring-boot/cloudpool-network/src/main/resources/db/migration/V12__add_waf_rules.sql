CREATE TABLE waf_rules (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    rule_type VARCHAR(50) NOT NULL, -- e.g., IP_BLOCK, RATE_LIMIT, SQLI_BLOCK, XSS_BLOCK
    pattern VARCHAR(255) NOT NULL, -- e.g., 192.168.1.1, 10 (req/sec), DROP TABLE
    action VARCHAR(50) NOT NULL, -- e.g., BLOCK, LOG
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_waf_rules_project ON waf_rules(project_id);
CREATE INDEX idx_waf_rules_type ON waf_rules(rule_type);
