use reqwest::Method;
use serde_json::Value;

use crate::client::CloudPoolClient;
use crate::errors::CloudPoolError;

/// Client for network management: tunnels, domains, Pub/Sub, and WAF rules.
pub struct NetworkClient<'a> {
    client: &'a CloudPoolClient,
}

impl<'a> NetworkClient<'a> {
    /// Create a new network client.
    pub fn new(client: &'a CloudPoolClient) -> Self {
        Self { client }
    }

    /// List all network tunnels.
    pub async fn list_tunnels(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "network/tunnels"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Create a new network tunnel.
    pub async fn create_tunnel(&self, config: Value) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "network/tunnels").json(&config))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Delete a network tunnel by ID.
    pub async fn delete_tunnel(&self, tunnel_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("network/tunnels/{}", tunnel_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::DELETE, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List all custom domains.
    pub async fn list_domains(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "network/domains"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Add a new custom domain.
    pub async fn add_domain(&self, domain: &str, target: Option<&str>) -> Result<Value, CloudPoolError> {
        let mut body = serde_json::json!({ "domain": domain });
        if let Some(t) = target {
            body["target"] = serde_json::json!(t);
        }
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "network/domains").json(&body))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Remove a custom domain by ID.
    pub async fn remove_domain(&self, domain_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("network/domains/{}", domain_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::DELETE, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List all Pub/Sub users.
    pub async fn list_pubsub_users(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "network/pubsub/users"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Create a new Pub/Sub user.
    pub async fn create_pubsub_user(&self, config: Value) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(
                self.client
                    .request(Method::POST, "network/pubsub/users")
                    .json(&config),
            )
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Delete a Pub/Sub user by ID.
    pub async fn delete_pubsub_user(&self, user_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("network/pubsub/users/{}", user_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::DELETE, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List all WAF rules.
    pub async fn list_waf_rules(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "network/waf/rules"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Create a new WAF rule.
    pub async fn create_waf_rule(&self, config: Value) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(
                self.client
                    .request(Method::POST, "network/waf/rules")
                    .json(&config),
            )
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Delete a WAF rule by ID.
    pub async fn delete_waf_rule(&self, rule_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("network/waf/rules/{}", rule_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::DELETE, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }
}
