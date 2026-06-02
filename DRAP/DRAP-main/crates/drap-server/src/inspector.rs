use serde::Serialize;
use std::sync::Arc;
use crate::dashboard::{DashboardBroadcaster, DashboardEvent};
use tokio::sync::RwLock;
use std::collections::VecDeque;
use uuid::Uuid;
use chrono::{DateTime, Utc};
use crate::db::Database;

#[derive(Debug, Clone, Serialize)]
#[derive(Serialize, Clone)]
pub struct TimingBreakdown {
    pub tls_handshake_ms: f64,
    pub routing_ms: f64,
    pub tunnel_transit_ms: f64,
    pub local_processing_ms: f64,
}

pub struct CapturedRequest {
    pub id: String,
    pub tunnel_id: String,
    pub timestamp: DateTime<Utc>,
    pub method: String,
    pub path: String,
    pub host: String,
    pub headers: Vec<(String, String)>,
    pub duration_ms: Option<f64>,
    pub timing: Option<TimingBreakdown>,
    pub is_binary: bool,
    pub hex_snippet: Option<String>,
    pub raw_request: Option<Bytes>,
}

pub struct Inspector {
    history: Arc<RwLock<VecDeque<CapturedRequest>>>,
    max_size: usize,
    db: Option<Arc<Database>>,
    broadcaster: Arc<DashboardBroadcaster>,
}

impl Inspector {
    pub fn new(max_size: usize, db: Option<Arc<Database>>, broadcaster: Arc<DashboardBroadcaster>) -> Self {
        Self {
            history: Arc::new(RwLock::new(VecDeque::with_capacity(max_size))),
            max_size,
            db,
            broadcaster,
        }
    }

    pub async fn record_request(&self, tunnel_id: &str, metadata: crate::data_server::RequestMetadata, raw_request: Option<Bytes>, duration_ms: Option<f64>, id: String) {
        // Detect binary bodies (Section 10.4.3)
        let is_binary = metadata.headers.iter().any(|(n, v)| {
            let name = n.to_lowercase();
            name == "content-type" && (v.contains("image") || v.contains("octet-stream") || v.contains("video"))
        });

        let request = CapturedRequest {
            id,
            tunnel_id: tunnel_id.to_string(),
            timestamp: Utc::now(),
            method: metadata.method,
            path: metadata.path,
            host: metadata.host,
            headers: metadata.headers,
            duration_ms,
            timing: None, 
            is_binary,
            hex_snippet: metadata.hex_snippet,
            raw_request,
        };

        // 1. In-memory buffer (for fast real-time push)
        {
            let mut history = self.history.write().await;
            if history.len() >= self.max_size {
                history.pop_front();
            }
            history.push_back(request.clone());
        }

        // 2. Real-time broadcast
        self.broadcaster.broadcast(DashboardEvent::Request(request.clone()));

        // 2. Persistent storage
        if let Some(db) = &self.db {
            if let Err(e) = db.record_request(&request).await {
                tracing::error!("Failed to persist request to DB: {:?}", e);
            }
        }
    }

    pub async fn record_request_with_timing(&self, tunnel_id: &str, metadata: crate::data_server::RequestMetadata, raw_request: Option<Bytes>, duration_ms: f64, timing: TimingBreakdown, id: String) {
        let is_binary = metadata.headers.iter().any(|(n, v)| {
            let name = n.to_lowercase();
            name == "content-type" && (v.contains("image") || v.contains("octet-stream") || v.contains("video"))
        });

        let request = CapturedRequest {
            id,
            tunnel_id: tunnel_id.to_string(),
            timestamp: Utc::now(),
            method: metadata.method,
            path: metadata.path,
            host: metadata.host,
            headers: metadata.headers,
            duration_ms: Some(duration_ms),
            timing: Some(timing),
            is_binary,
            hex_snippet: metadata.hex_snippet,
            raw_request,
        };

        {
            let mut history = self.history.write().await;
            if history.len() >= self.max_size {
                history.pop_front();
            }
            history.push_back(request.clone());
        }

        self.broadcaster.broadcast(DashboardEvent::Request(request.clone()));

        if let Some(db) = &self.db {
            let _ = db.record_request(&request).await;
        }
    }

    pub async fn get_history(&self) -> Vec<CapturedRequest> {
        // If we have a DB, we could fetch from there, but for the dashboard "live" view, 
        // the in-memory buffer is usually what we want.
        // For a full search feature, we would go to the DB.
        let history = self.history.read().await;
        history.iter().cloned().collect()
    }

    pub async fn get_request_by_id(&self, id: &str) -> Option<CapturedRequest> {
        let history = self.history.read().await;
        history.iter().find(|r| r.id == id).cloned()
    }
}
