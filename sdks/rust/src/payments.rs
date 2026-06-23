use reqwest::Method;
use serde_json::Value;

use crate::client::CloudPoolClient;
use crate::errors::CloudPoolError;

/// Client for payment management: gateways, plans, invoices, checkout, and usage.
pub struct PaymentsClient<'a> {
    client: &'a CloudPoolClient,
}

impl<'a> PaymentsClient<'a> {
    /// Create a new payments client.
    pub fn new(client: &'a CloudPoolClient) -> Self {
        Self { client }
    }

    /// List all connected payment gateways.
    pub async fn list_gateways(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "payments/gateways"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Connect a new payment gateway.
    pub async fn connect_gateway(&self, config: Value) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(
                self.client
                    .request(Method::POST, "payments/gateways")
                    .json(&config),
            )
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Disconnect (remove) a payment gateway by ID.
    pub async fn disconnect_gateway(&self, gateway_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("payments/gateways/{}", gateway_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::DELETE, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List all available subscription plans.
    pub async fn list_plans(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "payments/plans"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List all invoices for the authenticated account.
    pub async fn list_invoices(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "payments/invoices"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Create a checkout session for a given plan.
    pub async fn create_checkout(&self, plan_id: &str) -> Result<Value, CloudPoolError> {
        let body = serde_json::json!({ "planId": plan_id });
        let res = self
            .client
            .send_with_retry(
                self.client
                    .request(Method::POST, "payments/checkout")
                    .json(&body),
            )
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List usage records for the current billing period.
    pub async fn list_usage(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "payments/usage"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }
}
