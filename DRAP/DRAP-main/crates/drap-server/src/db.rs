use sqlx::postgres::PgPool;
use anyhow::{Context, Result};
use crate::inspector::CapturedRequest;

pub struct Database {
    pool: PgPool,
}

impl Database {
    pub async fn new(url: &str) -> Result<Self> {
        let pool = PgPool::connect(url)
            .await
            .context("Failed to connect to PostgreSQL")?;
        
        let this = Self { pool };
        this.init_db().await?;
        Ok(this)
    }

    async fn init_db(&self) -> Result<()> {
        sqlx::query(r#"
            CREATE TABLE IF NOT EXISTS request_history (
                id UUID PRIMARY KEY,
                tunnel_id TEXT NOT NULL,
                timestamp TIMESTAMPTZ NOT NULL,
                method TEXT NOT NULL,
                path TEXT NOT NULL,
                host TEXT NOT NULL,
                headers JSONB NOT NULL,
                duration_ms FLOAT8,
                hex_snippet TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_req_tunnel_ts ON request_history(tunnel_id, timestamp DESC);
        "#).execute(&self.pool).await?;
        Ok(())
    }

    pub async fn record_request(&self, request: &CapturedRequest) -> Result<()> {
        let headers_json = serde_json::to_value(&request.headers)?;
        
        sqlx::query(
            r#"
            INSERT INTO request_history (id, tunnel_id, timestamp, method, path, host, headers, duration_ms, hex_snippet)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
            "#
        )
        .bind(uuid::Uuid::parse_str(&request.id)?)
        .bind(&request.tunnel_id)
        .bind(request.timestamp)
        .bind(&request.method)
        .bind(&request.path)
        .bind(&request.host)
        .bind(headers_json)
        .bind(request.duration_ms)
        .bind(&request.hex_snippet)
        .execute(&self.pool)
        .await?;

        Ok(())
    }

    pub async fn prune_history(&self) -> Result<u64> {
        let result = sqlx::query(
            "DELETE FROM request_history WHERE timestamp < NOW() - INTERVAL '24 hours'"
        )
        .execute(&self.pool)
        .await?;
        
        Ok(result.rows_affected())
    }

    pub async fn get_history(&self, limit: i64) -> Result<Vec<CapturedRequest>> {
        let rows = sqlx::query(
            r#"
            SELECT id, tunnel_id, timestamp, method, path, host, headers
            FROM request_history
            ORDER BY timestamp DESC
            LIMIT $1
            "#
        )
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;

        let mut history = Vec::new();
        for row in rows {
            use sqlx::Row;
            let id: uuid::Uuid = row.get("id");
            let tunnel_id: String = row.get("tunnel_id");
            let timestamp: chrono::DateTime<chrono::Utc> = row.get("timestamp");
            let method: String = row.get("method");
            let path: String = row.get("path");
            let host: String = row.get("host");
            let headers_val: serde_json::Value = row.get("headers");
            
            let headers: Vec<(String, String)> = serde_json::from_value(headers_val)?;
            history.push(CapturedRequest {
                id: id.to_string(),
                tunnel_id,
                timestamp,
                method,
                path,
                host,
                headers,
            });
        }

        Ok(history)
    }

    pub async fn register_tunnel(&self, subdomain: &str) -> Result<()> {
        sqlx::query(
            r#"
            INSERT INTO tunnels (subdomain)
            VALUES ($1)
            ON CONFLICT (subdomain) DO UPDATE SET last_active = CURRENT_TIMESTAMP
            "#
        )
        .bind(subdomain)
        .execute(&self.pool)
        .await?;
        
        Ok(())
    }
}
