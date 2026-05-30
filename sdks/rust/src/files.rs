use std::error::Error;
use std::path::Path;
use tokio::fs::File;
use tokio::io::AsyncWriteExt;
use reqwest::multipart;
use reqwest::Method;
use serde_json::Value;
use futures_util::StreamExt;

use crate::client::CloudPoolClient;

pub struct FilesClient<'a> {
    client: &'a CloudPoolClient,
}

impl<'a> FilesClient<'a> {
    pub fn new(client: &'a CloudPoolClient) -> Self {
        Self { client }
    }

    /// Upload a file to a specific storage bucket.
    pub async fn upload(&self, file_path: &str, bucket: Option<&str>) -> Result<Value, Box<dyn Error>> {
        let path = Path::new(file_path);
        if !path.exists() {
            return Err(format!("File not found: {}", file_path).into());
        }

        let file_name = path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("upload_file")
            .to_string();

        let file_bytes = tokio::fs::read(file_path).await?;
        let file_part = multipart::Part::bytes(file_bytes)
            .file_name(file_name);

        let mut form = multipart::Form::new().part("file", file_part);

        if let Some(b) = bucket {
            form = form.text("bucket", b.to_string());
        }

        let res = self.client.request(Method::POST, "files/upload")
            .multipart(form)
            .send()
            .await?;

        let res = self.client.handle_response(res).await?;
        let json_body = res.json::<Value>().await?;
        Ok(json_body)
    }

    /// List all metadata records of the user's files.
    pub async fn list(&self) -> Result<Value, Box<dyn Error>> {
        let res = self.client.request(Method::GET, "files")
            .send()
            .await?;

        let res = self.client.handle_response(res).await?;
        let json_body = res.json::<Value>().await?;
        Ok(json_body)
    }

    /// Download a file by ID and stream it to the local output path.
    pub async fn download(&self, file_id: &str, output_path: &str) -> Result<(), Box<dyn Error>> {
        let path = format!("files/download/{}", file_id);
        let res = self.client.request(Method::GET, &path)
            .send()
            .await?;

        let res = self.client.handle_response(res).await?;
        let mut file = File::create(output_path).await?;
        let mut stream = res.bytes_stream();

        while let Some(chunk_result) = stream.next().await {
            let chunk = chunk_result?;
            file.write_all(&chunk).await?;
        }

        file.flush().await?;
        Ok(())
    }

    /// Share a file with an email address or generate a public link.
    pub async fn share(&self, file_id: &str, email: Option<&str>, expiry_hours: Option<i32>) -> Result<Value, Box<dyn Error>> {
        let path = format!("files/{}/share", file_id);
        let mut body = serde_json::Map::new();
        
        if let Some(e) = email {
            body.insert("sharedWithEmail".to_string(), Value::String(e.to_string()));
        }
        if let Some(h) = expiry_hours {
            body.insert("expiryHours".to_string(), Value::Number(h.into()));
        }

        let res = self.client.request(Method::POST, &path)
            .json(&Value::Object(body))
            .send()
            .await?;

        let res = self.client.handle_response(res).await?;
        let json_body = res.json::<Value>().await?;
        Ok(json_body)
    }

    /// Download a shared file anonymously via share token.
    pub async fn download_shared(&self, token: &str, output_path: &str) -> Result<(), Box<dyn Error>> {
        let path = format!("files/shared/{}", token);
        let res = self.client.request(Method::GET, &path)
            .send()
            .await?;

        let res = self.client.handle_response(res).await?;
        let mut file = File::create(output_path).await?;
        let mut stream = res.bytes_stream();

        while let Some(chunk_result) = stream.next().await {
            let chunk = chunk_result?;
            file.write_all(&chunk).await?;
        }

        file.flush().await?;
        Ok(())
    }
}
