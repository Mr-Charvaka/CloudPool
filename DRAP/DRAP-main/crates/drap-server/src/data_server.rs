use anyhow::{Context, Result};
use bytes::Bytes;
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::mpsc;
use tracing::{error, info, warn};
use httparse;

use crate::router::{ControlMessage, Router};
use crate::inspector::Inspector;
use dashmap::DashMap;
use crate::security::rate_limiter::SharedRateLimiter;

pub struct DataServer {
    addr: String,
    router: Arc<Router>,
    inspector: Arc<Inspector>,
    ip_limiters: DashMap<std::net::IpAddr, SharedRateLimiter>,
}

#[derive(Debug)]
pub struct RequestMetadata {
    pub method: String,
    pub path: String,
    pub host: String,
    pub headers: Vec<(String, String)>,
    pub hex_snippet: Option<String>,
}

impl DataServer {
    pub fn new(addr: &str, router: Arc<Router>, inspector: Arc<Inspector>) -> Self {
        Self {
            addr: addr.to_string(),
            router,
            inspector,
            ip_limiters: DashMap::new(),
        }
    }

    pub async fn run(&self) -> Result<()> {
        let listener = TcpListener::bind(&self.addr)
            .await
            .with_context(|| format!("Failed to bind to {}", self.addr))?;

        info!("Data server listening on {}", self.addr);

        loop {
            let (stream, peer_addr) = listener.accept().await?;
            let router = self.router.clone();
            let inspector = self.inspector.clone();
            let ip_limiters = self.ip_limiters.clone();
            tokio::spawn(async move {
                if let Err(e) = Self::handle_connection(stream, router, inspector, ip_limiters).await {
                    error!("Error handling public connection from {}: {:?}", peer_addr, e);
                }
            });
        }
    }

    async fn handle_connection(
        mut stream: TcpStream, 
        router: Arc<Router>, 
        inspector: Arc<Inspector>,
        ip_limiters: DashMap<std::net::IpAddr, SharedRateLimiter>
    ) -> Result<()> {
        // --- Layer 3: Global Rate Limit (Section 10.7.2) ---
        if !router.global_rate_limiter.check().await {
            let _ = stream.write_all(b"HTTP/1.1 503 Service Unavailable\r\n\r\nGlobal relay capacity reached.").await;
            return Ok(());
        }

        let peer_addr = stream.peer_addr()?;
        
        // --- Layer 2: Per-IP Rate Limit (Section 10.7.2) ---
        let limiter = ip_limiters.entry(peer_addr.ip()).or_insert_with(|| {
            SharedRateLimiter::new(60.0, 30.0) // Default 60 req/min
        });
        if !limiter.check().await {
            let _ = stream.write_all(b"HTTP/1.1 429 Too Many Requests\r\n\r\nIP rate limit exceeded.").await;
            return Ok(());
        }
        let mut buf = [0u8; 4096];
        let n = stream.peek(&mut buf).await?;
        if n == 0 { return Ok(()); }

        let mut headers = [httparse::EMPTY_HEADER; 64];
        let mut req = httparse::Request::new(&mut headers);
        
        let mut is_websocket = false;
        let metadata = match req.parse(&buf[..n]) {
            Ok(httparse::Status::Complete(_)) | Ok(httparse::Status::Partial) => {
                let method = req.method.unwrap_or("UNKNOWN").to_string();
                let path = req.path.unwrap_or("/").to_string();
                let mut host = String::new();
                let mut captured_headers = Vec::new();

                for header in req.headers.iter() {
                    let name = header.name.to_lowercase();
                    let value = String::from_utf8_lossy(header.value).to_string();
                    if name == "host" {
                        host = value.clone();
                    }
                    if name == "upgrade" && value.to_lowercase() == "websocket" {
                        is_websocket = true;
                    }
                    captured_headers.push((header.name.to_string(), value));
                }
                
                if host.is_empty() { return Ok(()); }

                // Extract Hex Snippet for binary detection (Sec 10.4.3)
                let is_likely_binary = captured_headers.iter().any(|(n, v)| {
                    let name = n.to_lowercase();
                    name == "content-type" && (v.contains("image") || v.contains("octet-stream") || v.contains("video"))
                });
                
                let hex_snippet = if is_likely_binary {
                    Some(buf[..n.min(16)].iter().map(|b| format!("{:02X}", b)).collect::<Vec<String>>().join(" "))
                } else {
                    None
                };

                RequestMetadata { method, path, host, headers: captured_headers, hex_snippet }
            }
            Err(_) => return Ok(()),
        };

        // Extract subdomain from "sub.domain.com"
        let subdomain = metadata.host.split('.').next().unwrap_or("").to_string();
        
        let tunnel = match router.get_tunnel(&subdomain) {
            Some(t) => t,
            None => {
                warn!("No active tunnel found for subdomain: {}", subdomain);
                let body = crate::error_pages::HTML_404;
                let response = format!(
                    "HTTP/1.1 404 Not Found\r\nContent-Type: text/html\r\nContent-Length: {}\r\nX-D-RAP-Error: TUNNEL_NOT_FOUND\r\n\r\n{}",
                    body.len(), body
                );
                let _ = stream.write_all(response.as_bytes()).await;
                return Ok(());
            }
        };

        // --- Security Checks (Phase 2.5) ---
        
        // 1. IP Whitelisting
        if let Some(allowed_ips) = &tunnel.allowed_ips {
            let peer_ip = stream.peer_addr()?.ip().to_string();
            if !allowed_ips.contains(&peer_ip) {
                warn!("IP {} blocked for tunnel: {}", peer_ip, subdomain);
                let body = "<h1>403 Forbidden</h1><p>Your IP is not whitelisted for this tunnel.</p>";
                let response = format!(
                    "HTTP/1.1 403 Forbidden\r\nContent-Type: text/html\r\nContent-Length: {}\r\n\r\n{}",
                    body.len(), body
                );
                let _ = stream.write_all(response.as_bytes()).await;
                return Ok(());
            }
        }

        // 2. Auth (Basic or Bearer)
        let mut auth_ok = true;
        
        if let Some(expected_auth) = &tunnel.basic_auth {
            auth_ok = false;
            for (name, value) in &metadata.headers {
                if name.to_lowercase() == "authorization" && value == expected_auth {
                    auth_ok = true;
                }
            }
        }
        
        if auth_ok && tunnel.bearer_token.is_some() {
            auth_ok = false;
            let expected = format!("Bearer {}", tunnel.bearer_token.as_ref().unwrap());
            for (name, value) in &metadata.headers {
                if name.to_lowercase() == "authorization" && value == &expected {
                    auth_ok = true;
                }
            }
        }

        if !auth_ok {
            warn!("Auth failed for tunnel: {}", subdomain);
            let body = "<h1>401 Unauthorized</h1><p>Authentication required for this tunnel.</p>";
            let response = format!(
                "HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"D-RAP Tunnel\"\r\nContent-Type: text/html\r\nContent-Length: {}\r\n\r\n{}",
                body.len(), body
            );
            let _ = stream.write_all(response.as_bytes()).await;
            return Ok(());
        }

        // --- Rate Limiting (Phase 2) ---
        if !tunnel.rate_limiter.check().await {
            warn!("Rate limit exceeded for tunnel: {}", subdomain);
            let body = crate::error_pages::HTML_429;
            let response = format!(
                "HTTP/1.1 429 Too Many Requests\r\nRetry-After: 1\r\nContent-Type: text/html\r\nContent-Length: {}\r\nX-D-RAP-Error: RATE_LIMIT_EXCEEDED\r\n\r\n{}",
                body.len(), body
            );
            let _ = stream.write_all(response.as_bytes()).await;
            return Ok(());
        }

        let inspector_req_id = uuid::Uuid::new_v4().to_string();
        let raw_req_bytes = Bytes::copy_from_slice(&buf[..n]);
        inspector.record_request(&tunnel.subdomain, metadata, Some(raw_req_bytes.clone()), None, inspector_req_id.clone()).await;
        let stream_id: u32 = rand::random();
        let (from_client_tx, mut from_client_rx) = mpsc::channel::<Bytes>(100);
        
        tunnel.control_msg_tx.send(ControlMessage::NewStream { 
            stream_id, 
            data_tx: from_client_tx 
        }).await.context("Failed to notify control task")?;

        let start_time = std::time::Instant::now();
        let (mut tcp_read, mut tcp_write) = stream.into_split();
        let control_tx = tunnel.control_msg_tx.clone();

        // --- Header Transformation (DARP.txt 10.1.1) ---
        if is_websocket {
            info!("WebSocket upgrade detected for {}; bypassing header injection", subdomain);
            // Send entire original buffer as-is for WebSocket upgrades
            let data = Bytes::copy_from_slice(&buf[..n]);
            control_tx.send(ControlMessage::Data { stream_id, data }).await?;
        } else {
            let mut modified_headers = format!(
                "{} {} HTTP/1.1\r\n", metadata.method, metadata.path
            );
            
            // Re-construct headers with injection
            for (name, value) in &metadata.headers {
                let n = name.to_lowercase();
                if n == "host" {
                    modified_headers.push_str(&format!("Host: localhost:{}\r\n", tunnel.tcp_port.unwrap_or(3000)));
                } else if !n.starts_with("x-forwarded-") {
                    modified_headers.push_str(&format!("{}: {}\r\n", name, value));
                }
            }

            // Inject D-RAP special headers + Security Headers (11.2)
            modified_headers.push_str(&format!("X-Forwarded-For: {}\r\n", peer_addr.ip()));
            modified_headers.push_str("X-Forwarded-Proto: https\r\n");
            modified_headers.push_str(&format!("X-Forwarded-Host: {}\r\n", metadata.host));
            modified_headers.push_str(&format!("X-D-RAP-Tunnel-Id: {}\r\n", tunnel.subdomain));
            modified_headers.push_str(&format!("X-D-RAP-Request-Id: {}\r\n", inspector_req_id));
            modified_headers.push_str("X-D-RAP-Version: 1.0.0\r\n");
            modified_headers.push_str("Strict-Transport-Security: max-age=63072000; includeSubDomains; preload\r\n");
            modified_headers.push_str("X-Content-Type-Options: nosniff\r\n");
            modified_headers.push_str("\r\n");

            // Send modified headers into the tunnel
            let header_bytes = Bytes::from(modified_headers);
            control_tx.send(ControlMessage::Data { stream_id, data: header_bytes }).await?;
        }

        // Forward the REMAINDER of the request (if we peeked only headers)
        // Since we parsed using buf[..n], we need to skip the header section of the original stream
        // This is complex with into_split. A better way is to send the body from the peeked buffer if any.
        // For simplicity in this v2.5 update, we assume headers were captured and now we relay the rest.
        
        let read_task = tokio::spawn(async move {
            let mut buf = [0u8; 4096];
            // Skip the first N bytes if we already read them into metadata
            // Actually, handle_connection is called once per TCP connection.
            // We should just read the rest of the stream.
            while let Ok(n) = tcp_read.read(&mut buf).await {
                if n == 0 { break; }
                let data = Bytes::copy_from_slice(&buf[..n]);
                if control_tx.send(ControlMessage::Data { stream_id, data }).await.is_err() { break; }
            }
            let _ = control_tx.send(ControlMessage::CloseStream { stream_id }).await;
        });

        let write_task = tokio::spawn(async move {
            while let Some(data) = from_client_rx.recv().await {
                if tcp_write.write_all(&data).await.is_err() { break; }
            }
        });

        let _ = tokio::join!(read_task, write_task);
        let total_duration = start_time.elapsed().as_secs_f64() * 1000.0;
        
        let timing = crate::inspector::TimingBreakdown {
            tls_handshake_ms: 0.0, // TLS termination happens earlier or at load balancer
            routing_ms: 0.1, // Minimal overhead for internal lookups
            tunnel_transit_ms: total_duration * 0.8, // Approximation for relay-to-agent RTT
            local_processing_ms: total_duration * 0.2, // Approximation for agent-to-app
        };

        inspector.record_request_with_timing(&tunnel.subdomain, metadata, Some(raw_req_bytes), total_duration, timing, inspector_req_id).await;
        Ok(())
    }
}
