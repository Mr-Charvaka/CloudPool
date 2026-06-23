use reqwest::Method;
use serde_json::Value;

use crate::client::CloudPoolClient;
use crate::errors::CloudPoolError;

/// Client for authentication and API-key management.
pub struct AuthClient<'a> {
    client: &'a CloudPoolClient,
}

impl<'a> AuthClient<'a> {
    /// Create a new auth client.
    pub fn new(client: &'a CloudPoolClient) -> Self {
        Self { client }
    }

    /// Get the currently authenticated user profile.
    pub async fn me(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "auth/me"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Sign in with email and password.
    pub async fn login(&self, email: &str, password: &str) -> Result<Value, CloudPoolError> {
        let body = serde_json::json!({ "email": email, "password": password });
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "auth/login").json(&body))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Register a new account.
    pub async fn register(&self, email: &str, password: &str, name: Option<&str>) -> Result<Value, CloudPoolError> {
        let mut body = serde_json::json!({ "email": email, "password": password });
        if let Some(n) = name {
            body["name"] = serde_json::json!(n);
        }
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "auth/register").json(&body))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Refresh the JWT token using a refresh token.
    pub async fn refresh_token(&self, refresh_token: &str) -> Result<Value, CloudPoolError> {
        let body = serde_json::json!({ "refreshToken": refresh_token });
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "auth/refresh").json(&body))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Generate a new API key.
    pub async fn generate_api_key(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "auth/api-keys"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List all API keys for the authenticated user.
    pub async fn list_api_keys(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "auth/api-keys"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Revoke (delete) an API key by ID.
    pub async fn revoke_api_key(&self, key_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("auth/api-keys/{}", key_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::DELETE, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Get usage analytics for a specific API key.
    pub async fn get_api_key_analytics(&self, key_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("auth/api-keys/{}/analytics", key_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }
}
