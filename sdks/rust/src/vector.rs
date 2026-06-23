use reqwest::Method;
use serde_json::{json, Value};

use crate::client::CloudPoolClient;
use crate::errors::CloudPoolError;

/// Client for vector search engine operations.
pub struct VectorClient<'a> {
    client: &'a CloudPoolClient,
}

impl<'a> VectorClient<'a> {
    /// Create a new vector client.
    pub fn new(client: &'a CloudPoolClient) -> Self {
        Self { client }
    }

    /// Search across uploaded files semantically.
    pub async fn search_files(&self, query: &str) -> Result<Value, CloudPoolError> {
        let res = self
            .client
            .send_with_retry(
                self.client
                    .request(Method::GET, "vector/search")
                    .query(&[("q", query)]),
            )
            .await?;
        let json_body = res.json::<Value>().await.map_err(|e| CloudPoolError::Network(e.to_string()))?;
        Ok(json_body)
    }

    /// Create a custom developer vector collection (Weaviate Class).
    pub async fn create_collection(
        &self,
        name: &str,
        description: &str,
        dimension: i32,
        distance_metric: &str,
    ) -> Result<Value, CloudPoolError> {
        let query = r#"
            mutation CreateCollection($name: String!, $description: String, $dimension: Int!, $distanceMetric: String) {
                createCollection(name: $name, description: $description, dimension: $dimension, distanceMetric: $distanceMetric) {
                    id
                    name
                    description
                    dimension
                    distanceMetric
                }
            }
        "#;

        let variables = json!({
            "name": name,
            "description": description,
            "dimension": dimension,
            "distanceMetric": distance_metric,
        });

        let payload = json!({ "query": query, "variables": variables });

        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "/graphql").json(&payload))
            .await?;
        let json_body: Value = res.json().await.map_err(|e| CloudPoolError::Network(e.to_string()))?;

        if let Some(errors) = json_body.get("errors") {
            return Err(CloudPoolError::GraphQL(errors.to_string()));
        }

        Ok(json_body["data"]["createCollection"].clone())
    }

    /// Index a document with content and optional metadata mapping.
    pub async fn index_document(
        &self,
        collection_id: &str,
        doc_id: &str,
        content: &str,
        metadata: Option<Value>,
    ) -> Result<Value, CloudPoolError> {
        let query = r#"
            mutation IndexDocument($collectionId: ID!, $docId: String!, $content: String!, $metadata: [KeyValueInput!]) {
                indexDocument(collectionId: $collectionId, docId: $docId, content: $content, metadata: $metadata) {
                    id
                    docId
                    content
                    metadata
                }
            }
        "#;

        let gql_metadata = match metadata {
            Some(Value::Object(map)) => {
                let list: Vec<Value> = map
                    .into_iter()
                    .map(|(k, v)| {
                        let val_str = match v {
                            Value::String(s) => s,
                            other => other.to_string(),
                        };
                        json!({"key": k, "value": val_str})
                    })
                    .collect();
                Value::Array(list)
            }
            _ => Value::Null,
        };

        let variables = json!({
            "collectionId": collection_id,
            "docId": doc_id,
            "content": content,
            "metadata": gql_metadata,
        });

        let payload = json!({ "query": query, "variables": variables });

        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "/graphql").json(&payload))
            .await?;
        let json_body: Value = res.json().await.map_err(|e| CloudPoolError::Network(e.to_string()))?;

        if let Some(errors) = json_body.get("errors") {
            return Err(CloudPoolError::GraphQL(errors.to_string()));
        }

        Ok(json_body["data"]["indexDocument"].clone())
    }

    /// Perform a semantic search on a specific developer vector collection.
    pub async fn search(
        &self,
        collection_id: &str,
        query_text: &str,
        limit: Option<i32>,
    ) -> Result<Value, CloudPoolError> {
        let query = r#"
            query SemanticSearch($collectionId: ID!, $query: String!, $limit: Int) {
                semanticSearch(collectionId: $collectionId, query: $query, limit: $limit) {
                    docId
                    content
                    score
                }
            }
        "#;

        let variables = json!({
            "collectionId": collection_id,
            "query": query_text,
            "limit": limit.unwrap_or(10),
        });

        let payload = json!({ "query": query, "variables": variables });

        let res = self
            .client
            .send_with_retry(self.client.request(Method::POST, "/graphql").json(&payload))
            .await?;
        let json_body: Value = res.json().await.map_err(|e| CloudPoolError::Network(e.to_string()))?;

        if let Some(errors) = json_body.get("errors") {
            return Err(CloudPoolError::GraphQL(errors.to_string()));
        }

        Ok(json_body["data"]["semanticSearch"].clone())
    }
}
