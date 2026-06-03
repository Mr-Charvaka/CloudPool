CREATE TABLE cron_jobs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(255) NOT NULL,
    target_url TEXT NOT NULL,
    http_method VARCHAR(20) NOT NULL DEFAULT 'POST',
    payload TEXT,
    headers TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cron_project_name UNIQUE (project_id, name)
);

CREATE TABLE cron_executions (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES cron_jobs(id) ON DELETE CASCADE,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    http_status INTEGER,
    response_body TEXT,
    is_success BOOLEAN NOT NULL
);

CREATE INDEX idx_cron_jobs_project ON cron_jobs(project_id);
CREATE INDEX idx_cron_executions_job ON cron_executions(job_id);
