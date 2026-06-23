use rand::Rng;
use std::future::Future;
use std::time::Duration;
use tokio::time::sleep;

/// Configuration for retry behaviour with exponential backoff and jitter.
#[derive(Debug, Clone)]
pub struct RetryConfig {
    /// Maximum number of retry attempts (does not include the initial request).
    pub max_retries: u32,
    /// Base delay before the first retry; doubled each subsequent attempt.
    pub base_delay: Duration,
    /// Hard cap on the computed exponential delay.
    pub max_delay: Duration,
    /// Hard cap on any individual sleep, including a `Retry-After` hint from the server.
    pub max_retry_sleep: Duration,
}

impl Default for RetryConfig {
    fn default() -> Self {
        Self {
            max_retries: 3,
            base_delay: Duration::from_millis(500),
            max_delay: Duration::from_secs(30),
            max_retry_sleep: Duration::from_secs(10),
        }
    }
}

/// Control whether an error type can trigger a retry.
pub trait IsRetryable {
    /// Return `true` if a retry should be attempted.
    fn is_retryable(&self) -> bool;
    /// Optional hint from the server indicating how long to wait before retrying.
    fn retry_after(&self) -> Option<Duration> {
        None
    }
}

/// Execute the async `operation` with exponential backoff and jitter.
///
/// If the operation returns a retryable error it is re-invoked up to
/// `config.max_retries` times.  Between each attempt the delay follows
/// `base_delay * 2^(attempt-1)` clamped to `max_delay`, with ±25 % jitter.
/// When the error carries a `retry_after` hint it is used instead, but
/// always capped at `max_retry_sleep`.
pub async fn retry<T, E, F, Fut>(config: &RetryConfig, mut operation: F) -> Result<T, E>
where
    F: FnMut() -> Fut,
    Fut: Future<Output = Result<T, E>>,
    E: IsRetryable,
{
    let mut attempt = 0;
    let mut last_retry_after: Option<Duration> = None;

    loop {
        if attempt > 0 {
            let delay = match last_retry_after {
                Some(after) => after.min(config.max_retry_sleep),
                None => backoff_delay(config, attempt),
            };
            sleep(delay).await;
        }

        match operation().await {
            Ok(value) => return Ok(value),
            Err(e) => {
                if !e.is_retryable() || attempt >= config.max_retries {
                    return Err(e);
                }
                last_retry_after = e.retry_after();
            }
        }
        attempt += 1;
    }
}

fn backoff_delay(config: &RetryConfig, attempt: u32) -> Duration {
    let mut delay = config.base_delay;
    for _ in 1..attempt {
        delay = delay.saturating_mul(2);
        if delay >= config.max_delay {
            delay = config.max_delay;
            break;
        }
    }
    delay = delay.min(config.max_delay);

    let fraction: f64 = rand::thread_rng().gen_range(0.75..1.25);
    let jittered = delay.mul_f64(fraction);
    jittered.min(config.max_delay)
}
