use anyhow::{Context, Result};
use bytes::Bytes;
use std::collections::HashMap;
use std::sync::Arc;
use std::sync::atomic::Ordering;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;
use tokio::sync::mpsc;
use tokio_rustls::rustls;
use tokio_rustls::TlsAcceptor;
use tracing::{error, info, warn};
use drap_protocol::frame_type::FrameType;
use drap_protocol::{Frame, DRAP_MAGIC, PROTOCOL_VERSION};
use drap_protocol::codec::DrapCodec;
use tokio_util::codec::Framed;
use futures::{StreamExt, SinkExt};

use crate::router::{ControlMessage, Router};
use crate::tcp_server::TcpTunnelServer;

pub struct ControlServer {
    acceptor: TlsAcceptor,
    addr: String,
    router: Arc<Router>,
}

impl ControlServer {
    pub fn new(tls_config: rustls::ServerConfig, addr: &str, router: Arc<Router>) -> Self {
        Self {
            acceptor: TlsAcceptor::from(Arc::new(tls_config)),
            addr: addr.to_string(),
            router,
        }
    }

    pub async fn run(&self) -> Result<()> {
        let listener = TcpListener::bind(&self.addr)
            .await
            .with_context(|| format!("Failed to bind to {}", self.addr))?;

        info!("Control server listening on {} (Pointer: {:p})", self.addr, &*self.router);

        loop {
            let (stream, peer_addr) = listener.accept().await?;
            info!("Accepted connection from {}", peer_addr);

            let acceptor = self.acceptor.clone();
            let router = self.router.clone();
            tokio::spawn(async move {
                if let Err(e) = Self::handle_connection(acceptor, stream, router).await {
                    error!("Error handling connection from {}: {:?}", peer_addr, e);
                }
            });
        }
    }

    async fn handle_connection(
        acceptor: TlsAcceptor, 
        stream: tokio::net::TcpStream,
        router: Arc<Router>
    ) -> Result<()> {
        let mut stream = acceptor.accept(stream).await.context("TLS handshake failed")?;
        info!("TLS handshake successful");

        // --- Handshake ---
        let mut client_magic = [0u8; 4];
        stream.read_exact(&mut client_magic).await?;
        if &client_magic != DRAP_MAGIC {
            return Err(anyhow::anyhow!("Invalid client magic"));
        }
        
        let client_version = stream.read_u16().await?;
        if client_version != PROTOCOL_VERSION {
            return Err(anyhow::anyhow!("Unsupported client protocol version: {}", client_version));
        }

        stream.write_all(DRAP_MAGIC).await?;
        stream.write_u16(PROTOCOL_VERSION).await?;

        let mut framed = Framed::new(stream, DrapCodec);

        // --- Auth (Structured Binary Sec 9.2) ---
        let auth_req = match framed.next().await {
            Some(Ok(f)) => f,
            _ => return Err(anyhow::anyhow!("Failed to read AUTH_REQ")),
        };
        if auth_req.payload.len() < 2 { return Err(anyhow::anyhow!("Malformed AUTH_REQ")); }
        
        let token_len = u16::from_be_bytes([auth_req.payload[0], auth_req.payload[1]]) as usize;
        if auth_req.payload.len() < 2 + token_len + 5 { return Err(anyhow::anyhow!("Truncated AUTH_REQ")); }
        
        let token = String::from_utf8_lossy(&auth_req.payload[2..2+token_len]);
        let client_ver_bytes = &auth_req.payload[2+token_len..2+token_len+2];
        let client_version = format!("{}.{}", client_ver_bytes[0], client_ver_bytes[1]);
        let os_type = auth_req.payload[2+token_len+2];
        let os_name = match os_type {
            0 => "Linux",
            1 => "macOS",
            2 => "Windows",
            _ => "Unknown",
        };

        if token != "my-secret-token" {
            warn!("Authentication failed for token: {}", token);
            let fail = Frame::new(FrameType::AuthFail, 0, 0, Bytes::from("Invalid Token"));
            framed.send(fail).await?;
            return Err(anyhow::anyhow!("Authentication failed"));
        }
        framed.send(Frame::new(FrameType::AuthOk, 0, 0, Bytes::from("OK"))).await?;
        
        // Tunnel Req
        let tunnel_req = match framed.next().await {
            Some(Ok(f)) => f,
            _ => return Err(anyhow::anyhow!("Failed to read TunnelReq")),
        };
        let (requested_subdomain, allowed_ips, basic_auth, is_tcp) = if tunnel_req.payload.is_empty() {
            (None, None, None, false)
        } else {
            match serde_json::from_slice::<serde_json::Value>(&tunnel_req.payload) {
                Ok(v) => (
                    v["subdomain"].as_str().map(|s| s.to_string()),
                    v["allowed_ips"].as_array().map(|arr| arr.iter().filter_map(|i| i.as_str().map(|s| s.to_string())).collect()),
                    v["basic_auth"].as_str().map(|s| s.to_string()),
                    v["protocol"].as_str().map(|s| s.to_uppercase() == "TCP").unwrap_or(false)
                ),
                Err(_) => (Some(String::from_utf8_lossy(&tunnel_req.payload).to_string()), None, None, false)
            }
        };

        // --- Multiplexing Loop Setup ---

        let (msg_tx, mut msg_rx) = mpsc::channel::<ControlMessage>(100);
        let mut tunnel = router.register_tunnel(
            requested_subdomain, 
            msg_tx, 
            allowed_ips, 
            basic_auth, 
            None, 
            os_name.to_string(), 
            client_version
        ).await
            .map_err(|e| anyhow::anyhow!("Registration failed: {}", e))?;
        
        // If TCP, spawn the TCP listener server
        let mut tcp_port = None;
        if is_tcp {
            let tcp_server = TcpTunnelServer::new(router.clone(), tunnel.subdomain.clone());
            match tcp_server.run().await {
                Ok(port) => {
                    tcp_port = Some(port);
                    // Update tunnel object (Arc swap or interior mutability? Router needs to handle this)
                    // For now, let's just use it in the response.
                }
                Err(e) => {
                    error!("Failed to start TCP tunnel server: {:?}", e);
                }
            }
        }

        let public_url = if let Some(port) = tcp_port {
            format!("{}.{}:{}", tunnel.subdomain, router.base_domain, port)
        } else {
            format!("{}.{}", tunnel.subdomain, router.base_domain)
        };
        stream.write_all(&Frame::new(FrameType::TunnelCreated, 0, 0, Bytes::from(public_url.clone())).encode()).await?;
        info!("Tunnel Established: {}", public_url);

        // Notify Dashboard
        router.broadcaster.broadcast(crate::dashboard::DashboardEvent::TunnelConnected(tunnel.snapshot()));

        let (tx_queue_tx, mut tx_queue_rx) = mpsc::channel::<Frame>(1000);
        let mut framed = framed;
        
        // --- 1. Central Writer Task (Ensures sequenced socket access) ---
        let tunnel_clone_for_writer = tunnel.clone();
        let writer_task = tokio::spawn(async move {
            while let Some(frame) = tx_queue_rx.recv().await {
                let len = frame.header.length;
                if framed.send(frame).await.is_err() { break; }
                tunnel_clone_for_writer.bytes_sent.fetch_add(len as u64, Ordering::Relaxed);
            }
        });

        let mut active_streams: HashMap<u32, mpsc::Sender<Bytes>> = HashMap::new();
        let mut stream_worker_txs: HashMap<u32, mpsc::Sender<Bytes>> = HashMap::new();
        let mut stream_windows: HashMap<u32, Arc<tokio::sync::Semaphore>> = HashMap::new();
        
        let mut heartbeat = tokio::time::interval(std::time::Duration::from_secs(30));

        loop {
            tokio::select! {
                _ = heartbeat.tick() => {
                    let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as u64;
                    let ping = Frame::new(FrameType::Ping, 0, 0, Bytes::from(now.to_be_bytes().to_vec()));
                    let _ = tx_queue_tx.send(ping).await;
                }
                // 1. Data/Commands from DataServer (Internal)
                Some(msg) = msg_rx.recv() => {
                    match msg {
                        ControlMessage::NewStream { stream_id, data_tx } => {
                            // Enforce 256 stream limit (Section 8.3)
                            if tunnel.active_streams_count.load(Ordering::Relaxed) >= 256 {
                                warn!("Stream limit reached for tunnel: {}", tunnel.subdomain);
                                continue;
                            }

                            tunnel.active_streams_count.fetch_add(1, Ordering::Relaxed);
                            active_streams.insert(stream_id, data_tx);
                            
                            let sem = Arc::new(tokio::sync::Semaphore::new(65535));
                            stream_windows.insert(stream_id, sem.clone());
                            
                            let (worker_tx, mut worker_rx) = mpsc::channel::<Bytes>(100);
                            stream_worker_txs.insert(stream_id, worker_tx);

                            let tx_queue_tx = tx_queue_tx.clone();
                            tokio::spawn(async move {
                                while let Some(data) = worker_rx.recv().await {
                                    let len = data.len() as u32;
                                    // Acquire window permits (True Flow Control - blocks only this worker)
                                    let _ = sem.acquire_many(len).await;
                                    
                                    let frame = Frame::new(FrameType::Data, 0, stream_id, data);
                                    if tx_queue_tx.send(frame).await.is_err() { break; }
                                }
                            });

                            // Notify client to open local connection, include subdomain in payload
                            let payload = Bytes::from(tunnel.subdomain.clone());
                            let _ = tx_queue_tx.send(Frame::new(FrameType::StreamOpen, 0, stream_id, payload)).await;
                        }
                        ControlMessage::Data { stream_id, data } => {
                            if let Some(tx) = stream_worker_txs.get(&stream_id) {
                                let _ = tx.send(data).await;
                            }
                        }
                        ControlMessage::CloseStream { stream_id } => {
                            stream_windows.remove(&stream_id);
                            stream_worker_txs.remove(&stream_id);
                            if active_streams.remove(&stream_id).is_some() {
                                tunnel.active_streams_count.fetch_sub(1, Ordering::Relaxed);
                            }
                            let frame = Frame::new(FrameType::StreamClose, 0, stream_id, Bytes::new());
                            let _ = tx_queue_tx.send(frame).await;
                        }
                        ControlMessage::Replay { raw_request } => {
                            let frame = Frame::new(FrameType::Replay, 0, 0, raw_request);
                            let _ = tx_queue_tx.send(frame).await;
                        }
                        ControlMessage::UdpData { data, src_addr } => {
                            let mut payload = bytes::BytesMut::with_capacity(data.len() + 32);
                            let addr_str = src_addr.to_string();
                            payload.extend_from_slice(&(addr_str.len() as u8).to_be_bytes());
                            payload.extend_from_slice(addr_str.as_bytes());
                            payload.extend_from_slice(&data);
                            let frame = Frame::new(FrameType::UdpData, 0, 0, payload.freeze());
                            let _ = tx_queue_tx.send(frame).await;
                        }
                        ControlMessage::GoAway { reason } => {
                            let frame = Frame::new(FrameType::GoAway, 0, 0, Bytes::from(reason));
                            let _ = tx_queue_tx.send(frame).await;
                        }
                    }
                }

                // 2. Data from Client (External Tunnel)
                res = framed.next() => {
                    let frame = match res {
                        Some(Ok(f)) => f,
                        _ => {
                            info!("Client disconnected");
                            break;
                        }
                    };

                    match frame.header.frame_type {
                            FrameType::Data => {
                                if let Some(tx) = active_streams.get(&frame.header.stream_id) {
                                    tunnel.bytes_recv.fetch_add(frame.payload.len() as u64, Ordering::Relaxed);
                                    if tx.send(frame.payload).await.is_err() {
                                        active_streams.remove(&frame.header.stream_id);
                                    }
                                }
                            }
                            FrameType::StreamClose => {
                                stream_windows.remove(&frame.header.stream_id);
                                stream_worker_txs.remove(&frame.header.stream_id);
                                if active_streams.remove(&frame.header.stream_id).is_some() {
                                    tunnel.active_streams_count.fetch_sub(1, Ordering::Relaxed);
                                }
                            }
                            FrameType::WindowUpdate => {
                                if frame.payload.len() == 4 {
                                    let mut buf = [0u8; 4];
                                    buf.copy_from_slice(&frame.payload);
                                    let increment = u32::from_be_bytes(buf);
                                    if let Some(sem) = stream_windows.get(&frame.header.stream_id) {
                                        sem.add_permits(increment as usize);
                                    }
                                }
                            }
                            FrameType::Pong => {
                                if frame.payload.len() == 8 {
                                    let mut buf = [0u8; 8];
                                    buf.copy_from_slice(&frame.payload);
                                    let sent_time = u64::from_be_bytes(buf);
                                    let now = std::time::SystemTime::now()
                                        .duration_since(std::time::UNIX_EPOCH)
                                        .unwrap_or_default()
                                        .as_millis() as u64;
                                    let rtt = (now.saturating_sub(sent_time)) as u32;
                                    tunnel.last_rtt_ms.store(rtt, Ordering::Relaxed);
                                }
                            }
                            FrameType::Ping => {
                                let pong = Frame::new(FrameType::Pong, 0, 0, frame.payload);
                                let _ = tx_queue_tx.send(pong).await;
                            }
                            _ => {}
                        }
                    }
                }
            }
        }
 
        info!("Removing tunnel: {}", tunnel.subdomain);
        router.broadcaster.broadcast(crate::dashboard::DashboardEvent::TunnelDisconnected(tunnel.subdomain.clone()));
        router.remove_tunnel(&tunnel.subdomain);
        Ok(())
    }
}
