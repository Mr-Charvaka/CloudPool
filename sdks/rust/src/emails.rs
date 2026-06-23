use reqwest::Method;
use serde_json::Value;

use crate::client::CloudPoolClient;
use crate::errors::CloudPoolError;

/// Client for sending emails and reading the inbox.
pub struct EmailsClient<'a> {
    client: &'a CloudPoolClient,
}

impl<'a> EmailsClient<'a> {
    /// Create a new emails client.
    pub fn new(client: &'a CloudPoolClient) -> Self {
        Self { client }
    }

    /// Send an email.
    pub async fn send(&self, to: &str, subject: &str, body: &str) -> Result<Value, CloudPoolError> {
        let payload = serde_json::json!({
            "to": to,
            "subject": subject,
            "body": body,
        });
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "emails/send").json(&payload))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List inbox messages.
    pub async fn list_inbox(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "emails/inbox"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }
}
