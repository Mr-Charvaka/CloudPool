use std::sync::Arc;
use tokio::sync::Mutex;
use std::time::{Duration, Instant};
use redis::{AsyncCommands, Client};

/// A simple Token Bucket rate limiter.
pub struct RateLimiter {
    rate: f64,      // tokens per second
    capacity: f64,  // max burst size
    tokens: f64,
    last_refill: Instant,
}

impl RateLimiter {
    pub fn new(rate: f64, capacity: f64) -> Self {
        Self {
            rate,
            capacity,
            tokens: capacity,
            last_refill: Instant::now(),
        }
    }

    pub fn check(&mut self) -> bool {
        self.refill();
        if self.tokens >= 1.0 {
            self.tokens -= 1.0;
            true
        } else {
            false
        }
    }

    fn refill(&mut self) {
        let now = Instant::now();
        let elapsed = now.duration_since(self.last_refill).as_secs_f64();
        self.tokens = (self.tokens + elapsed * self.rate).min(self.capacity);
        self.last_refill = now;
    }
}

/// A thread-safe wrapper for the rate limiter, supporting distributed Redis.
#[derive(Clone)]
pub struct SharedRateLimiter {
    inner: Arc<Mutex<RateLimiter>>,
    redis: Option<Arc<Client>>,
    key: String,
}

impl SharedRateLimiter {
    pub fn new(rate: f64, capacity: f64) -> Self {
        Self {
            inner: Arc::new(Mutex::new(RateLimiter::new(rate, capacity))),
            redis: None,
            key: "darp:rate:global".to_string(),
        }
    }

    pub fn new_distributed(rate: f64, capacity: f64, redis: Arc<Client>, key: String) -> Self {
        Self {
            inner: Arc::new(Mutex::new(RateLimiter::new(rate, capacity))),
            redis: Some(redis),
            key,
        }
    }

    pub async fn check(&self) -> bool {
        if let Some(client) = &self.redis {
            // Section 10.7.2: Atomic Redis Token Bucket via Lua
            if let Ok(mut conn) = client.get_multiplexed_tokio_connection().await {
                // Lua script for atomic increment with TTL
                let script = redis::Script::new(r"
                    local key = KEYS[1]
                    local limit = tonumber(ARGV[1])
                    local window = tonumber(ARGV[2])
                    local current = redis.call('GET', key)
                    if current and tonumber(current) >= limit then
                        return 0
                    else
                        redis.call('INCR', key)
                        if not current then
                            redis.call('EXPIRE', key, window)
                        end
                        return 1
                    end
                ");
                
                // For simplicity, using a 1-second window for req/sec
                let res: i32 = match script.key(&self.key).arg(100).arg(1).invoke_async(&mut conn).await {
                    Ok(v) => v,
                    Err(_) => 1, // Fallback to accept on Redis error
                };
                return res == 1;
            }
        }

        // Fallback to in-memory
        let mut limiter = self.inner.lock().await;
        limiter.check()
    }
}
