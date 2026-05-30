use reqwest::{Client, Method, RequestBuilder, Response};
use std::error::Error;

use crate::files::FilesClient;
use crate::database::DatabaseClient;
use crate::vector::VectorClient;

pub struct CloudPoolClient {
    pub(crate) base_url: String,
    pub(crate) client: Client,
    pub(crate) api_key: Option<String>,
    pub(crate) jwt_token: Option<String>,
}

impl CloudPoolClient {
    /// Create a new CloudPool client instance.
    pub fn new(base_url: &str, api_key: Option<String>, jwt_token: Option<String>) -> Self {
        Self {
            base_url: base_url.trim_end_matches('/').to_string(),
            client: Client::new(),
            api_key,
            jwt_token,
        }
    }

    /// Accessor for files/storage operations.
    pub fn files(&self) -> FilesClient<'_> {
        FilesClient::new(self)
    }

    /// Accessor for database orchestration operations.
    pub fn database(&self) -> DatabaseClient<'_> {
        DatabaseClient::new(self)
    }

    /// Accessor for vector search engine operations.
    pub fn vector(&self) -> VectorClient<'_> {
        VectorClient::new(self)
    }

    /// Build a new authenticated request.
    pub(crate) fn request(&self, method: Method, path: &str) -> RequestBuilder {
        let url = if path.starts_with('/') {
            if self.base_url.ends_with("/api") {
                format!("{}{}", &self.base_url[..self.base_url.len() - 4], path)
            } else {
                // Parse host if possible, or fallback to relative
                format!("{}/{}", self.base_url, path.trim_start_matches('/'))
            }
        } else {
            format!("{}/{}", self.base_url, path.trim_start_matches('/'))
        };
        let mut builder = self.client.request(method, &url);

        if let Some(ref key) = self.api_key {
            builder = builder.header("X-API-KEY", key);
        } else if let Some(ref token) = self.jwt_token {
            builder = builder.header("Authorization", format!("Bearer {}", token));
        }

        builder
    }

    /// Execute request and handle status code checking.
    pub(crate) async fn handle_response(&self, response: Response) -> Result<Response, Box<dyn Error>> {
        let status = response.status();
        if status.is_client_error() || status.is_server_error() {
            let body_text = response.text().await.unwrap_or_default();
            // Try parsing JSON error format
            if let Ok(json_val) = serde_json::from_str::<serde_json::Value>(&body_text) {
                if let Some(err_msg) = json_val.get("error").and_then(|e| e.get("message")).and_then(|m| m.as_str()) {
                    let err_code = json_val.get("error").and_then(|e| e.get("code")).and_then(|c| c.as_str()).unwrap_or("API_ERROR");
                    return Err(format!("CloudPool API Error [{}]: {}", err_code, err_msg).into());
                }
            }
            return Err(format!("CloudPool API Request Failed with status {}: {}", status, body_text).into());
        }
        Ok(response)
    }
}
