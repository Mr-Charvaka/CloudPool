pub mod tls;

use serde::{Deserialize, Serialize};
use std::fmt;
use uuid::Uuid;
use std::path::PathBuf;
use std::sync::OnceLock;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct TunnelId(Uuid);

impl TunnelId {
    pub fn new() -> Self {
        Self(Uuid::new_v4())
    }

    pub fn random() -> Self {
        Self(Uuid::new_v4())
    }
}

impl Default for TunnelId {
    fn default() -> Self {
        Self::new()
    }
}

impl fmt::Display for TunnelId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct StreamId(pub u32);

impl fmt::Display for StreamId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(thiserror::Error, Debug)]
pub enum DrapError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Protocol error: {0}")]
    Protocol(String),
}

static AUTH_TOKEN: OnceLock<String> = OnceLock::new();

fn default_config_dir() -> PathBuf {
    dirs::config_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("drap")
}

pub fn load_or_generate_auth_token() -> String {
    AUTH_TOKEN.get_or_init(|| {
        if let Ok(token) = std::env::var("DRAP_AUTH_TOKEN") {
            if !token.is_empty() {
                tracing::info!("Loaded DRAP_AUTH_TOKEN from environment variable");
                return token;
            }
        }

        let config_dir = default_config_dir();
        let token_file = config_dir.join("auth_token");
        if let Ok(content) = std::fs::read_to_string(&token_file) {
            let token = content.trim().to_string();
            if !token.is_empty() {
                tracing::info!("Loaded auth token from {}", token_file.display());
                return token;
            }
        }

        let token = uuid::Uuid::new_v4().to_string();
        if std::fs::create_dir_all(&config_dir).is_ok() {
            if std::fs::write(&token_file, &token).is_ok() {
                tracing::info!("Generated new auth token and saved to {}", token_file.display());
            }
        }
        tracing::warn!("No DRAP_AUTH_TOKEN set; generated random token: {}", token);
        token
    }).clone()
}

pub fn get_auth_token() -> Option<String> {
    std::env::var("DRAP_AUTH_TOKEN").ok().filter(|t| !t.is_empty())
        .or_else(|| {
            let config_dir = default_config_dir();
            let token_file = config_dir.join("auth_token");
            std::fs::read_to_string(&token_file).ok()
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
        })
}
