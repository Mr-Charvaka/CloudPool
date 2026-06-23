use tokio::sync::broadcast;
use serde::Serialize;
use crate::inspector::CapturedRequest;
use crate::router::TunnelSnapshot;

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type", content = "data")]
pub enum DashboardEvent {
    Request(CapturedRequest),
    TunnelConnected(TunnelSnapshot),
    TunnelDisconnected(String),
    MetricsUpdate {
        total_requests: u64,
        active_tunnels: usize,
    },
}

pub struct DashboardBroadcaster {
    tx: broadcast::Sender<DashboardEvent>,
}

impl DashboardBroadcaster {
    pub fn new() -> Self {
        let (tx, _) = broadcast::channel(100);
        Self { tx }
    }

    pub fn subscribe(&self) -> broadcast::Receiver<DashboardEvent> {
        self.tx.subscribe()
    }

    pub fn broadcast(&self, event: DashboardEvent) {
        let _ = self.tx.send(event);
    }
}
