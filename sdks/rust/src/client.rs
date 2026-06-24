use std::time::Duration;

use reqwest::{Client, Method, RequestBuilder, Response};

use crate::auth::AuthClient;
use crate::compute::ComputeClient;
use crate::database::DatabaseClient;
use crate::emails::EmailsClient;
use crate::errors::{parse_error_response, CloudPoolError};
use crate::files::FilesClient;
use crate::kv::KvClient;
use crate::network::NetworkClient;
use crate::payments::PaymentsClient;
use crate::retry::{retry, RetryConfig};
use crate::vector::VectorClient;

/// Top-level client for the CloudPool platform API.
pub struct CloudPoolClient {
    pub(crate) base_url: String,
    pub(crate) client: Client,
    pub(crate) api_key: Option<String>,
    pub(crate) jwt_token: Option<String>,
    pub(crate) retry_config: RetryConfig,
}

impl CloudPoolClient {
    /// Create a new CloudPool client with default retry configuration.
    pub fn new(base_url: &str, api_key: Option<String>, jwt_token: Option<String>) -> Self {
        Self {
            base_url: base_url.trim_end_matches('/').to_string(),
            client: Client::builder().timeout(Duration::from_secs(30)).build().unwrap_or_else(|_| Client::new()),
            api_key,
            jwt_token,
            retry_config: RetryConfig::default(),
        }
    }

    /// Override the default retry configuration.
    pub fn with_retry_config(mut self, retry_config: RetryConfig) -> Self {
        self.retry_config = retry_config;
        self
    }

    /// Accessor for file storage operations.
    pub fn files(&self) -> FilesClient<'_> {
        FilesClient::new(self)
    }

    /// Accessor for database orchestration operations.
    pub fn database(&self) -> DatabaseClient<'_> {
        DatabaseClient::new(self)
    }

    /// Accessor for vector search engine operations.
    pub fn vector(&self) -> VectorClient<'_> {
        VectorClient::new(self)
    }

    /// Accessor for authentication and API-key management.
    pub fn auth(&self) -> AuthClient<'_> {
        AuthClient::new(self)
    }

    /// Accessor for compute deployments, cron jobs, serverless, and logs.
    pub fn compute(&self) -> ComputeClient<'_> {
        ComputeClient::new(self)
    }

    /// Accessor for network management (tunnels, domains, Pub/Sub, WAF).
    pub fn network(&self) -> NetworkClient<'_> {
        NetworkClient::new(self)
    }

    /// Accessor for payment management.
    pub fn payments(&self) -> PaymentsClient<'_> {
        PaymentsClient::new(self)
    }

    /// Accessor for the Key-Value store.
    pub fn kv(&self) -> KvClient<'_> {
        KvClient::new(self)
    }

    /// Accessor for email operations.
    pub fn emails(&self) -> EmailsClient<'_> {
        EmailsClient::new(self)
    }

    /// Build a new authenticated request.
    pub(crate) fn request(&self, method: Method, path: &str) -> RequestBuilder {
        let url = if path.starts_with('/') {
            if self.base_url.ends_with("/api") {
                format!("{}{}", &self.base_url[..self.base_url.len() - 4], path)
            } else {
                format!("{}/{}", self.base_url, path.trim_start_matches('/'))
            }
        } else {
            format!("{}/{}", self.base_url, path.trim_start_matches('/'))
        };
        let mut builder = self.client.request(method, &url);

        if let Some(ref key) = self.api_key {
            builder = builder.header("X-API-KEY", key);
        } else if let Some(ref token) = self.jwt_token {
            builder = builder.header("Authorization", format!("Bearer {}", token));
        }

        builder
    }

    /// Send a request built with [`request()`](Self::request) and handle the
    /// response, applying retry logic for retryable failures.
    pub(crate) async fn send_with_retry(&self, builder: RequestBuilder) -> Result<Response, CloudPoolError> {
        let req = builder
            .build()
            .map_err(|e| CloudPoolError::Http(0, format!("request build failed: {}", e)))?;

        let Some(cloneable) = req.try_clone() else {
            let resp = self
                .client
                .execute(req)
                .await
                .map_err(|e| CloudPoolError::Network(e.to_string()))?;
            return Self::check_response(resp).await;
        };

        let cfg = self.retry_config.clone();
        let inner = self.client.clone();

        retry(&cfg, move || {
            let req_clone_result = cloneable.try_clone().ok_or_else(|| CloudPoolError::Http(0, "request body is not cloneable, cannot retry".to_string()));
            let inner = inner.clone();
            async move {
                let req = req_clone_result?;
                let resp = inner
                    .execute(req)
                    .await
                    .map_err(|e| CloudPoolError::Network(e.to_string()))?;
                Self::check_response(resp).await
            }
        })
        .await
    }

    /// Inspect the HTTP status of a response and return an error if necessary.
    async fn check_response(resp: Response) -> Result<Response, CloudPoolError> {
        let status = resp.status();
        if status.is_success() {
            return Ok(resp);
        }
        let retry_after = parse_retry_after(&resp);
        let body = match resp.text().await {
            Ok(text) => text,
            Err(e) => return Err(CloudPoolError::Network(e.to_string())),
        };
        Err(parse_error_response(status.as_u16(), &body, retry_after))
    }
}

/// Extract the `Retry-After` header value as a [`Duration`], if present.
fn parse_retry_after(resp: &Response) -> Option<Duration> {
    resp.headers()
        .get("Retry-After")?
        .to_str()
        .ok()
        .and_then(|s| s.parse::<u64>().ok())
        .map(Duration::from_secs)
}

impl crate::retry::IsRetryable for CloudPoolError {
    fn is_retryable(&self) -> bool {
        matches!(
            self,
            CloudPoolError::RateLimit { .. } | CloudPoolError::Network(_)
        )
    }

    fn retry_after(&self) -> Option<Duration> {
        match self {
            CloudPoolError::RateLimit { retry_after, .. } => *retry_after,
            _ => None,
        }
    }
}
