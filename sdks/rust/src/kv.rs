use reqwest::Method;
use serde_json::Value;

use crate::client::CloudPoolClient;
use crate::errors::CloudPoolError;

/// Client for the CloudPool Key-Value store.
pub struct KvClient<'a> {
    client: &'a CloudPoolClient,
}

impl<'a> KvClient<'a> {
    /// Create a new KV client.
    pub fn new(client: &'a CloudPoolClient) -> Self {
        Self { client }
    }

    /// Get the value for a given key.
    pub async fn get(&self, key: &str) -> Result<Value, CloudPoolError> {
        let path = format!("kv/{}", key);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Set a key-value pair.
    pub async fn set(&self, key: &str, value: Value) -> Result<Value, CloudPoolError> {
        let path = format!("kv/{}", key);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, &path).json(&value))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Delete a key-value pair.
    pub async fn delete(&self, key: &str) -> Result<Value, CloudPoolError> {
        let path = format!("kv/{}", key);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::DELETE, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List all keys in the KV store.
    pub async fn list(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "kv"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }
}
