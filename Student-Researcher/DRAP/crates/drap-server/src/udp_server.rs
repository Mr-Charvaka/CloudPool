use anyhow::Result;
use bytes::Bytes;
use std::sync::Arc;
use tokio::net::UdpSocket;
use tracing::{error, info};

use crate::router::{ControlMessage, Router};

pub struct UdpServer {
    addr: String,
    router: Arc<Router>,
}

impl UdpServer {
    pub fn new(addr: &str, router: Arc<Router>) -> Self {
        Self {
            addr: addr.to_string(),
            router,
        }
    }

    pub async fn run_for_tunnel(&self, subdomain: String) -> Result<()> {
        let socket = UdpSocket::bind(&self.addr).await?;
        let port = socket.local_addr()?.port();
        info!("UDP Tunnel Listener for {} on port {}", subdomain, port);

        let mut buf = [0u8; 4096];
        loop {
            let (n, src_addr) = socket.recv_from(&mut buf).await?;
            let data = Bytes::copy_from_slice(&buf[..n]);
            
            if let Some(tunnel) = self.router.get_tunnel(&subdomain) {
                let _ = tunnel.control_msg_tx.send(ControlMessage::UdpData { 
                    data, 
                    src_addr 
                }).await;
            } else {
                break;
            }
        }
        Ok(())
    }
}
