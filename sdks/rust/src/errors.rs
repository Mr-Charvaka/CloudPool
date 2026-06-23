use std::fmt;
use std::time::Duration;

/// Errors returned by the CloudPool API client.
#[derive(Debug)]
pub enum CloudPoolError {
    /// Authentication failure (401/403).
    Auth(String),
    /// Resource not found (404).
    NotFound(String),
    /// Rate limit exceeded (429), optionally with a server-suggested wait duration.
    RateLimit {
        /// Value of the `Retry-After` header, if present.
        retry_after: Option<Duration>,
        /// Response body text.
        message: String,
    },
    /// Request validation failure (400/422).
    Validation(String),
    /// Resource conflict (409).
    Conflict(String),
    /// GraphQL-level error returned inside the response body.
    GraphQL(String),
    /// Generic HTTP error with the status code and body text.
    Http(u16, String),
    /// Network-level failure (timeout, connection refused, DNS, etc.).
    Network(String),
}

impl fmt::Display for CloudPoolError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            CloudPoolError::Auth(msg) => write!(f, "Authentication error: {}", msg),
            CloudPoolError::NotFound(msg) => write!(f, "Not found: {}", msg),
            CloudPoolError::RateLimit { message, .. } => {
                write!(f, "Rate limit exceeded: {}", message)
            }
            CloudPoolError::Validation(msg) => write!(f, "Validation error: {}", msg),
            CloudPoolError::Conflict(msg) => write!(f, "Conflict: {}", msg),
            CloudPoolError::GraphQL(msg) => write!(f, "GraphQL error: {}", msg),
            CloudPoolError::Http(code, msg) => write!(f, "HTTP {} error: {}", code, msg),
            CloudPoolError::Network(msg) => write!(f, "Network error: {}", msg),
        }
    }
}

impl std::error::Error for CloudPoolError {}

impl From<reqwest::Error> for CloudPoolError {
    fn from(err: reqwest::Error) -> Self {
        if err.is_status() {
            if let Some(status) = err.status() {
                let msg = err.to_string();
                return match status.as_u16() {
                    401 | 403 => CloudPoolError::Auth(msg),
                    404 => CloudPoolError::NotFound(msg),
                    429 => CloudPoolError::RateLimit {
                        retry_after: None,
                        message: msg,
                    },
                    409 => CloudPoolError::Conflict(msg),
                    400 | 422 => CloudPoolError::Validation(msg),
                    code => CloudPoolError::Http(code, msg),
                };
            }
        }
        if err.is_timeout() || err.is_connect() {
            return CloudPoolError::Network(err.to_string());
        }
        CloudPoolError::Network(err.to_string())
    }
}

/// Build a [`CloudPoolError`] from an HTTP status code and response body.
///
/// The body is parsed as JSON looking for the common CloudPool error envelope
/// (`{"error":{"message":"...","code":"..."}}`).
pub fn parse_error_response(status: u16, body: &str, retry_after: Option<Duration>) -> CloudPoolError {
    let msg = extract_error_message(body)
        .unwrap_or_else(|| "Unknown API error".to_string());
    match status {
        401 | 403 => CloudPoolError::Auth(msg),
        404 => CloudPoolError::NotFound(msg),
        429 => CloudPoolError::RateLimit { retry_after, message: msg },
        409 => CloudPoolError::Conflict(msg),
        400 | 422 => CloudPoolError::Validation(msg),
        code => CloudPoolError::Http(code, msg),
    }
}

/// Extract the `error.message` field from a JSON API error body.
pub fn extract_error_message(body: &str) -> Option<String> {
    let val: serde_json::Value = serde_json::from_str(body).ok()?;
    val.get("error")
        .and_then(|e| e.get("message"))
        .and_then(|m| m.as_str())
        .map(|s| s.to_string())
        .or_else(|| {
            val.get("message")
                .and_then(|m| m.as_str())
                .map(|s| s.to_string())
        })
        .or_else(|| {
            val.get("error")
                .and_then(|e| e.as_str())
                .map(|s| s.to_string())
        })
}
