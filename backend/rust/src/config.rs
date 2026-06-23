use std::path::PathBuf;
use serde::{Serialize, Deserialize};

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct CliConfig {
    pub base_url: String,
    pub jwt_token: Option<String>,
    pub username: Option<String>,
}

impl Default for CliConfig {
    fn default() -> Self {
        Self {
            base_url: "http://localhost:8080/api".to_string(),
            jwt_token: None,
            username: None,
        }
    }
}

impl CliConfig {
    /// Get the path to the config file (~/.cloudpool/config.json).
    pub fn get_path() -> PathBuf {
        let home = std::env::var("USERPROFILE")
            .or_else(|_| std::env::var("HOME"))
            .unwrap_or_else(|_| ".".to_string());
        
        let mut path = PathBuf::from(home);
        path.push(".cloudpool");
        path.push("config.json");
        path
    }

    /// Load the config from file, or returns default config.
    pub fn load() -> Self {
        let path = Self::get_path();
        if path.exists() {
            if let Ok(content) = std::fs::read_to_string(path) {
                if let Ok(config) = serde_json::from_str::<CliConfig>(&content) {
                    return config;
                }
            }
        }
        Self::default()
    }

    /// Save the config to file.
    pub fn save(&self) -> Result<(), Box<dyn std::error::Error>> {
        let path = Self::get_path();
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let content = serde_json::to_string_pretty(self)?;
        std::fs::write(&path, &content)?;

        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o600))?;
        }
        // On Windows, file permissions inherit from the parent directory.
        // Users should ensure their home directory has appropriate ACLs.

        Ok(())
    }
}
