use anyhow::Result;
use std::sync::Arc;
use tokio::net::TcpListener;
use tokio::sync::mpsc;
use tracing::{error, info};
use bytes::Bytes;

use crate::router::{ControlMessage, Router, Tunnel};

pub struct TcpTunnelServer {
    router: Arc<Router>,
    tunnel_subdomain: String,
}

impl TcpTunnelServer {
    pub fn new(router: Arc<Router>, tunnel_subdomain: String) -> Self {
        Self {
            router,
            tunnel_subdomain,
        }
    }

    pub async fn run(&self) -> Result<u16> {
        let mut port = 40000;
        let listener = loop {
            match TcpListener::bind(format!("0.0.0.0:{}", port)).await {
                Ok(l) => break l,
                Err(_) => {
                    port += 1;
                    if port > 50000 {
                        return Err(anyhow::anyhow!("No available ports in range 40000-50000"));
                    }
                }
            }
        };
        
        let router = self.router.clone();
        let subdomain = self.tunnel_subdomain.clone();

        tokio::spawn(async move {
            info!("TCP Tunnel Listener active on port {}", port);
            loop {
                let (mut stream, peer_addr) = match listener.accept().await {
                    Ok(s) => s,
                    Err(e) => {
                        error!("TCP Listener accept failed: {:?}", e);
                        break;
                    }
                };

                let tunnel = match router.get_tunnel(&subdomain) {
                    Some(t) => t,
                    None => break,
                };

                let stream_id: u32 = rand::random();
                let (from_client_tx, mut from_client_rx) = mpsc::channel::<Bytes>(100);

                if let Err(e) = tunnel.control_msg_tx.send(ControlMessage::NewStream { 
                    stream_id, 
                    data_tx: from_client_tx 
                }).await {
                    error!("Failed to notify control task for TCP stream: {:?}", e);
                    break;
                }

                let control_tx = tunnel.control_msg_tx.clone();
                tokio::spawn(async move {
                    let (mut tcp_read, mut tcp_write) = stream.into_split();
                    
                    let read_task = tokio::spawn(async move {
                        let mut buf = [0u8; 4096];
                        while let Ok(n) = tcp_read.read(&mut buf).await {
                            if n == 0 { break; }
                            let data = Bytes::copy_from_slice(&buf[..n]);
                            if control_tx.send(ControlMessage::Data { stream_id, data }).await.is_err() { break; }
                        }
                        let _ = control_tx.send(ControlMessage::CloseStream { stream_id }).await;
                    });

                    let write_task = tokio::spawn(async move {
                        let mut reader = tokio_util::io::StreamReader::new(
                            tokio_stream::wrappers::ReceiverStream::new(from_client_rx)
                                .map(|b| Ok::<_, std::io::Error>(b))
                        );
                        let _ = tokio::io::copy(&mut reader, &mut tcp_write).await;
                    });

                    let _ = tokio::join!(read_task, write_task);
                    info!("TCP tunnel connection from {} closed", peer_addr);
                });
            }
        });

        Ok(port)
    }
}
