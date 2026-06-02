pub mod subdomain_gen;

use dashmap::DashMap;
use std::sync::Arc;
use std::sync::atomic::{AtomicU64, AtomicU32, Ordering};
use tokio::sync::mpsc;
use tracing::{info, warn};
use bytes::Bytes;
use serde::Serialize;
use crate::db::Database;
use crate::security::rate_limiter::SharedRateLimiter;
use crate::dashboard::DashboardBroadcaster;

#[derive(Debug)]
pub enum ControlMessage {
    NewStream { stream_id: u32, data_tx: mpsc::Sender<Bytes> },
    Data { stream_id: u32, data: Bytes },
    CloseStream { stream_id: u32 },
    Replay { raw_request: Bytes },
    UdpData { data: Bytes, src_addr: std::net::SocketAddr },
    GoAway { reason: String },
    WindowUpdate { stream_id: u32, increment: u32 },
}

pub struct Tunnel {
    pub subdomain: String,
    pub control_msg_tx: mpsc::Sender<ControlMessage>,
    pub bytes_sent: Arc<AtomicU64>,
    pub bytes_recv: Arc<AtomicU64>,
    pub rate_limiter: SharedRateLimiter,
    pub allowed_ips: Option<Vec<String>>,
    pub basic_auth: Option<String>,
    pub bearer_token: Option<String>,
    pub udp_port: Option<u16>,
    pub tcp_port: Option<u16>,
    pub last_rtt_ms: Arc<AtomicU32>,
    pub client_os: String,
    pub client_version: String,
    pub active_streams_count: Arc<AtomicU32>,
}

#[derive(Serialize)]
pub struct TunnelSnapshot {
    pub subdomain: String,
    pub bytes_sent: u64,
    pub bytes_recv: u64,
    pub udp_port: Option<u16>,
    pub tcp_port: Option<u16>,
    pub last_rtt_ms: u32,
    pub client_os: String,
    pub client_version: String,
    pub active_streams_count: u32,
}

impl Tunnel {
    pub fn snapshot(&self) -> TunnelSnapshot {
        TunnelSnapshot {
            subdomain: self.subdomain.clone(),
            bytes_sent: self.bytes_sent.load(Ordering::Relaxed),
            bytes_recv: self.bytes_recv.load(Ordering::Relaxed),
            udp_port: self.udp_port,
            tcp_port: self.tcp_port,
            last_rtt_ms: self.last_rtt_ms.load(Ordering::Relaxed),
            client_os: self.client_os.clone(),
            client_version: self.client_version.clone(),
            active_streams_count: self.active_streams_count.load(Ordering::Relaxed),
        }
    }
}

const RESERVED_SUBDOMAINS: &[&str] = &[
    "www", "api", "admin", "dashboard", "status", "mail", "ftp", "ssh",
    "relay", "control", "metrics", "health", "drap", "app", "cdn",
    "static", "assets", "media", "blog", "docs", "support", "help", "login"
];

pub struct Router {
    pub base_domain: String,
    tunnels: DashMap<String, Arc<Tunnel>>,
    db: Option<Arc<Database>>,
    pub global_rate_limiter: SharedRateLimiter,
    pub broadcaster: Arc<DashboardBroadcaster>,
}

impl Router {
    pub fn new(base_domain: &str, db: Option<Arc<Database>>, broadcaster: Arc<DashboardBroadcaster>) -> Self {
        Self {
            base_domain: base_domain.to_string(),
            tunnels: DashMap::new(),
            db,
            global_rate_limiter: SharedRateLimiter::new(50000, 1), // 50k req/sec
            broadcaster,
        }
    }

    pub fn generate_subdomain(&self) -> String {
        subdomain_gen::generate_random_subdomain()
    }

    pub async fn register_tunnel(
        &self, 
        requested_subdomain: Option<String>, 
        control_msg_tx: mpsc::Sender<ControlMessage>,
        allowed_ips: Option<Vec<String>>,
        basic_auth: Option<String>,
        bearer_token: Option<String>,
        client_os: String,
        client_version: String,
    ) -> Result<Arc<Tunnel>, String> {
        let mut subdomain = requested_subdomain.unwrap_or_else(|| self.generate_subdomain()).to_lowercase();
        
        let mut attempts = 0;
        while self.tunnels.contains_key(&subdomain) || RESERVED_SUBDOMAINS.contains(&subdomain.as_str()) {
            if requested_subdomain.is_some() {
                return Err(format!("Subdomain '{}' is taken or reserved.", subdomain));
            }
            subdomain = self.generate_subdomain();
            attempts += 1;
            if attempts > 5 {
                return Err("Failed to generate unique subdomain after 5 attempts".to_string());
            }
        }

        // --- UDP Port Allocation (Section 5.1) ---
        let mut udp_socket = None;
        let mut allocated_port = None;
        
        for _ in 0..10 { // Try 10 random ports
            let port = rand::random::<u16>() % 10000 + 10000;
            if let Ok(socket) = std::net::UdpSocket::bind(format!("0.0.0.0:{}", port)) {
                socket.set_nonblocking(true).unwrap();
                udp_socket = Some(tokio::net::UdpSocket::from_std(socket).unwrap());
                allocated_port = Some(port);
                break;
            }
        }

        let tunnel = Arc::new(Tunnel {
            subdomain: subdomain.clone(),
            control_msg_tx: control_msg_tx.clone(),
            bytes_sent: Arc::new(AtomicU64::new(0)),
            bytes_recv: Arc::new(AtomicU64::new(0)),
            rate_limiter: SharedRateLimiter::new(100.0, 50.0),
            allowed_ips,
            basic_auth,
            bearer_token,
            udp_port: allocated_port,
            tcp_port: None,
            last_rtt_ms: Arc::new(AtomicU32::new(0)),
            client_os,
            client_version,
            active_streams_count: Arc::new(AtomicU32::new(0)),
        });

        // Spawn UDP Relay Task if port was allocated
        if let (Some(socket), Some(port)) = (udp_socket, allocated_port) {
            let tx = control_msg_tx.clone();
            let sub = subdomain.clone();
            tokio::spawn(async move {
                let mut buf = [0u8; 2048];
                info!("UDP Relay active for {} on port {}", sub, port);
                loop {
                    match socket.recv_from(&mut buf).await {
                        Ok((n, src_addr)) => {
                            let data = Bytes::copy_from_slice(&buf[..n]);
                            let _ = tx.send(ControlMessage::UdpData { data, src_addr }).await;
                        }
                        Err(_) => break,
                    }
                }
            });
        }

        if let Some(db) = &self.db {
            let _ = db.register_tunnel(&subdomain).await;
        }

        self.tunnels.insert(subdomain.clone(), tunnel.clone());
        info!("Registered tunnel: {}.{}", subdomain, self.base_domain);
        Ok(tunnel)
    }

    pub fn remove_tunnel(&self, subdomain: &str) {
        self.tunnels.remove(subdomain);
    }

    pub fn get_tunnel(&self, subdomain: &str) -> Option<Arc<Tunnel>> {
        let sub = subdomain.to_lowercase();
        self.tunnels.get(&sub).map(|r| r.value().clone())
    }

    pub fn list_tunnels(&self) -> Vec<Arc<Tunnel>> {
        self.tunnels.iter().map(|r| r.value().clone()).collect()
    }

    pub async fn broadcast_goaway(&self, reason: &str) {
        info!("Broadcasting GOAWAY to all clients: {}", reason);
        for tunnel in self.tunnels.iter() {
            let _ = tunnel.control_msg_tx.send(ControlMessage::GoAway {
                reason: reason.to_string(),
            }).await;
        }
    }
}
