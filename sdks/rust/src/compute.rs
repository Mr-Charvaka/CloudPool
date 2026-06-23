use reqwest::Method;
use serde_json::Value;

use crate::client::CloudPoolClient;
use crate::errors::CloudPoolError;

/// Client for managing compute deployments, cron jobs, serverless functions, logs, and pods.
pub struct ComputeClient<'a> {
    client: &'a CloudPoolClient,
}

impl<'a> ComputeClient<'a> {
    /// Create a new compute client.
    pub fn new(client: &'a CloudPoolClient) -> Self {
        Self { client }
    }

    /// List all compute deployments.
    pub async fn list_deployments(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "compute/deployments"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Create a new compute deployment.
    pub async fn create_deployment(&self, config: Value) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "compute/deployments").json(&config))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Delete a compute deployment by ID.
    pub async fn delete_deployment(&self, deployment_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("compute/deployments/{}", deployment_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::DELETE, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List all cron jobs.
    pub async fn list_cron_jobs(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "compute/cron-jobs"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Create a new cron job.
    pub async fn create_cron_job(&self, config: Value) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "compute/cron-jobs").json(&config))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Delete a cron job by ID.
    pub async fn delete_cron_job(&self, job_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("compute/cron-jobs/{}", job_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::DELETE, &path))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List all serverless function deployments.
    pub async fn list_serverless(&self) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, "compute/serverless"))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// Deploy a new serverless function.
    pub async fn deploy_serverless(&self, config: Value) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "compute/serverless").json(&config))
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List logs for a specified resource.
    pub async fn list_logs(&self, resource_id: &str) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(
                self.client
                    .request(Method::GET, "compute/logs")
                    .query(&[("resourceId", resource_id)]),
            )
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }

    /// List pods for a given deployment or resource.
    pub async fn list_pods(&self, deployment_id: &str) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(
                self.client
                    .request(Method::GET, "compute/pods")
                    .query(&[("deploymentId", deployment_id)]),
            )
            .await?;
        Ok(res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?)
    }
}
