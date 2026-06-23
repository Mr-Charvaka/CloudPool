use serde::{Deserialize, Serialize};
use std::fs;
use std::collections::HashMap;
use anyhow::{Context, Result};
use tracing::warn;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TunnelConfig {
    #[serde(rename = "addr")]
    pub local_port: u16,
    pub subdomain: Option<String>,
    #[serde(default = "default_proto")]
    pub proto: String,
    pub auth: Option<String>,
    pub auth_token: Option<String>,
    pub allowed_ips: Option<Vec<String>>,
    pub inspect: Option<bool>,
}

fn default_proto() -> String { "http".to_string() }

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DrapConfig {
    pub authtoken: Option<String>,
    #[serde(default = "default_relay")]
    pub relay_host: String,
    #[serde(default)]
    pub region: String,
    pub tunnels: HashMap<String, TunnelConfig>,
}

fn default_relay() -> String { "empirebot.in".to_string() }

impl DrapConfig {
    pub fn from_file(path: &str) -> Result<Self> {
        let content = fs::read_to_string(path)
            .with_context(|| format!("Failed to read config file: {}", path))?;
        
        let config: DrapConfig = serde_yaml::from_str(&content)
            .with_context(|| "Failed to parse YAML config")?;
            
        Ok(config)
    }

    pub fn default_for_port(port: u16, requested_subdomain: Option<String>) -> Self {
        let mut tunnels = HashMap::new();
        tunnels.insert("default".to_string(), TunnelConfig {
            local_port: port,
            subdomain: requested_subdomain,
            proto: "http".to_string(),
            auth: None,
            auth_token: None,
            allowed_ips: None,
            inspect: Some(true),
        });
        Self {
            authtoken: None,
            relay_host: default_relay(),
            region: "us-east-1".to_string(),
            tunnels,
        }
    }

    /// Merges CLI arguments into the config
    pub fn merge_cli(&mut self, port: Option<u16>, subdomain: Option<String>) {
        if let Some(p) = port {
            if let Some(tunnel) = self.tunnels.get_mut("default") {
                tunnel.local_port = p;
            } else {
                self.tunnels.insert("default".to_string(), TunnelConfig {
                    local_port: p,
                    subdomain: subdomain.clone(),
                    proto: "http".to_string(),
                    auth: None,
                    auth_token: None,
                    allowed_ips: None,
                    inspect: Some(true),
                });
            }
        }
        
        if let Some(s) = subdomain {
             if let Some(tunnel) = self.tunnels.get_mut("default") {
                tunnel.subdomain = Some(s);
             }
        }
    }

    pub fn get_token(&self) -> Option<String> {
        self.authtoken.clone()
    }
}
