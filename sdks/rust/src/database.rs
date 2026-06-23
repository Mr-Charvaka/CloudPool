use reqwest::Method;
use serde_json::Value;

use crate::client::CloudPoolClient;
use crate::errors::CloudPoolError;

/// Client for dynamic relational database table operations.
pub struct DatabaseClient<'a> {
    client: &'a CloudPoolClient,
}

impl<'a> DatabaseClient<'a> {
    /// Create a new database client.
    pub fn new(client: &'a CloudPoolClient) -> Self {
        Self { client }
    }

    /// Provision a new dynamic relational database table.
    pub async fn create_table(
        &self,
        name: &str,
        display_name: &str,
        description: &str,
        fields: Value,
        project_id: Option<&str>,
    ) -> Result<Value, CloudPoolError> {
        let mut body = serde_json::Map::new();
        body.insert("name".to_string(), Value::String(name.to_string()));
        body.insert("displayName".to_string(), Value::String(display_name.to_string()));
        body.insert("description".to_string(), Value::String(description.to_string()));
        body.insert("fields".to_string(), fields);

        if let Some(pid) = project_id {
            body.insert("projectId".to_string(), Value::String(pid.to_string()));
        }

        let res = self
            .client
            .send_with_retry(
                self.client
                    .request(Method::POST, "v1/db/tables")
                    .json(&Value::Object(body)),
            )
            .await?;
        let json_body = res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?;
        Ok(json_body)
    }

    /// List all custom/dynamic database tables.
    pub async fn list_tables(&self, project_id: Option<&str>) -> Result<Value, CloudPoolError> {
        let mut req = self.client.request(Method::GET, "v1/db/tables");
        if let Some(pid) = project_id {
            req = req.query(&[("projectId", pid)]);
        }
        let res = self.client.send_with_retry(req).await?;
        let json_body = res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?;
        Ok(json_body)
    }

    /// Get a specific custom table definition by ID.
    pub async fn get_table(&self, table_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("v1/db/tables/{}", table_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, &path))
            .await?;
        let json_body = res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?;
        Ok(json_body)
    }

    /// Delete a custom relational table and drop its database structure.
    pub async fn delete_table(&self, table_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("v1/db/tables/{}", table_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::DELETE, &path))
            .await?;
        let json_body = res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?;
        Ok(json_body)
    }

    /// Insert a record into a dynamic custom database table.
    pub async fn insert_record(&self, table_id: &str, record: Value) -> Result<Value, CloudPoolError> {
        let path = format!("v1/db/tables/{}/records", table_id);
        let res = self
            .client
            .send_with_retry(
                self.client
                    .request(Method::POST, &path)
                    .json(&record),
            )
            .await?;
        let json_body = res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?;
        Ok(json_body)
    }

    /// Query all records inside a custom relational database table.
    pub async fn query_records(&self, table_id: &str) -> Result<Value, CloudPoolError> {
        let path = format!("v1/db/tables/{}/records", table_id);
        let res = self
            .client
            .send_with_retry(self.client.request(Method::GET, &path))
            .await?;
        let json_body = res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?;
        Ok(json_body)
    }
}
