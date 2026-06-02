use std::collections::HashMap;
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;
use tokio::sync::mpsc;
use tokio_rustls::rustls;
use tokio_rustls::TlsConnector;
use tokio_rustls::client::TlsStream;
use tracing::{error, info, warn};
use drap_protocol::frame_type::FrameType;
use drap_protocol::{Frame, DRAP_MAGIC, PROTOCOL_VERSION};
use drap_protocol::codec::DrapCodec;
use tokio_util::codec::Framed;
use futures::{StreamExt, SinkExt};

pub struct ControlConnection {
    connector: TlsConnector,
    addr: String,
    framed: Option<Framed<TlsStream<TcpStream>, DrapCodec>>,
    
    // Maps Subdomain -> Port
    tunnels: HashMap<String, u16>,
    active_streams: HashMap<u32, mpsc::Sender<Bytes>>,
    
    to_tunnel_tx: mpsc::Sender<(u32, Bytes)>,
    to_tunnel_rx: mpsc::Receiver<(u32, Bytes)>,
    close_stream_tx: mpsc::Sender<u32>,
    close_stream_rx: mpsc::Receiver<u32>,
}

impl ControlConnection {
    pub async fn new(addr: &str) -> Result<Self> {
        let mut config = rustls::ClientConfig::builder()
            .dangerous()
            .with_custom_certificate_verifier(Arc::new(NoCertificateVerification))
            .with_no_client_auth();
            
        config.alpn_protocols = vec![b"drap/1".to_vec()];

        let (to_tunnel_tx, to_tunnel_rx) = mpsc::channel(100);
        let (close_stream_tx, close_stream_rx) = mpsc::channel(100);

        Ok(Self {
            connector: TlsConnector::from(Arc::new(config)),
            addr: addr.to_string(),
            framed: None,
            tunnels: HashMap::new(),
            active_streams: HashMap::new(),
            to_tunnel_tx,
            to_tunnel_rx,
            close_stream_tx,
            close_stream_rx,
        })
    }

    pub async fn perform_handshake(&mut self) -> Result<()> {
        let stream = TcpStream::connect(&self.addr)
            .await
            .with_context(|| format!("Failed to connect to {}", self.addr))?;

        let domain = rustls::pki_types::ServerName::try_from("localhost")
            .map_err(|_| anyhow::anyhow!("Invalid domain"))?;

        let mut stream = self.connector.connect(domain, stream).await?;
        info!("TLS handshake successful");

        // --- Handshake ---
        stream.write_all(DRAP_MAGIC).await?;
        stream.write_u16(PROTOCOL_VERSION).await?;
        
        let mut server_magic = [0u8; 4];
        stream.read_exact(&mut server_magic).await?;
        if &server_magic != DRAP_MAGIC {
            return Err(anyhow::anyhow!("Invalid server magic"));
        }
        
        let server_version = stream.read_u16().await?;
        if server_version != PROTOCOL_VERSION {
            return Err(anyhow::anyhow!("Unsupported server protocol version: {}", server_version));
        }
        
        let mut framed = Framed::new(stream, DrapCodec);

        // --- Auth (Structured Binary Handshake Sec 9.2) ---
        let token = "my-secret-token";
        let mut auth_payload = bytes::BytesMut::with_capacity(32 + token.len());
        
        // 1. Token Length (2 bytes)
        auth_payload.extend_from_slice(&(token.len() as u16).to_be_bytes());
        // 2. Token (variable)
        auth_payload.extend_from_slice(token.as_bytes());
        // 3. Client Version (2 bytes: 1.0)
        auth_payload.extend_from_slice(&(0x0100u16).to_be_bytes());
        // 4. OS Type (1 byte: 2=Win)
        auth_payload.extend_from_slice(&[2u8]); 
        // 5. Capabilities Bitmask (2 bytes: 0x0000)
        auth_payload.extend_from_slice(&[0u8, 0u8]);

        let auth_req = Frame::new(FrameType::AuthReq, 0, 0, auth_payload.freeze());
        framed.send(auth_req).await?;

        let frame = match framed.next().await {
            Some(Ok(f)) => f,
            _ => return Err(anyhow::anyhow!("Connection lost during Auth")),
        };

        if frame.header.frame_type != FrameType::AuthOk {
            return Err(anyhow::anyhow!("Authentication failed"));
        }

        self.framed = Some(framed);
        Ok(())
    }
}
    pub async fn create_tunnel(&mut self, config: &crate::config::TunnelConfig) -> Result<()> {
        let framed = self.framed.as_mut().context("Not connected")?;
        
        let payload = serde_json::to_vec(config)?;
        framed.send(Frame::new(FrameType::TunnelReq, 0, 0, Bytes::from(payload))).await?;

        let frame = match framed.next().await {
            Some(Ok(f)) => f,
            _ => return Err(anyhow::anyhow!("Connection lost during TunnelReq")),
        };
        if frame.header.frame_type == FrameType::TunnelCreated {
            let public_url = String::from_utf8_lossy(&frame.payload).to_string();
            // Extract subdomain for local routing
            let sub = public_url.split('.').next().unwrap_or(&public_url).to_string();
            self.tunnels.insert(sub, config.local_port);
            info!("Tunnel Created! Public URL: https://{}", public_url);
            Ok(())
        } else {
            Err(anyhow::anyhow!("Tunnel request denied"))
        }
    }

    pub async fn run(&mut self) -> Result<()> {
        let mut framed = self.framed.take().context("No active stream")?;
        let (tx_queue_tx, mut tx_queue_rx) = mpsc::channel::<Frame>(1000);

        // --- 1. Central Writer Task ---
        let writer_task = tokio::spawn(async move {
            while let Some(frame) = tx_queue_rx.recv().await {
                if framed.send(frame).await.is_err() { break; }
            }
        });

        let mut stream_worker_txs: HashMap<u32, mpsc::Sender<Bytes>> = HashMap::new();
        let mut stream_windows: HashMap<u32, Arc<tokio::sync::Semaphore>> = HashMap::new();

        loop {
            tokio::select! {
                // 1. Data/Control from local channel -> Tunnel
                Some((stream_id, data)) = self.to_tunnel_rx.recv() => {
                    if stream_id == 0 {
                        let frame = Frame::decode(data).unwrap();
                        let _ = tx_queue_tx.send(frame).await;
                    } else {
                        if let Some(tx) = stream_worker_txs.get(&stream_id) {
                            let _ = tx.send(data).await;
                        }
                    }
                }

                Some(stream_id) = self.close_stream_rx.recv() => {
                    self.active_streams.remove(&stream_id);
                    stream_worker_txs.remove(&stream_id);
                    let frame = Frame::new(FrameType::StreamClose, 0, stream_id, Bytes::new());
                    let _ = tx_queue_tx.send(frame).await;
                }

                // 2. Data from Tunnel -> Local App
                res = framed.next() => {
                    let frame = match res {
                        Some(Ok(f)) => f,
                        _ => break,
                    };

                    match frame.header.frame_type {
                            FrameType::StreamOpen => {
                                let subdomain = String::from_utf8_lossy(&frame.payload).to_string();
                                let local_port = *self.tunnels.get(&subdomain).unwrap_or(&3000);
                                info!("Opening local connection to :{} for tunnel {}", local_port, subdomain);
                                let tx = self.spawn_forwarder(frame.header.stream_id, local_port).await;
                                self.active_streams.insert(frame.header.stream_id, tx);

                                // Spawn Agent Worker Task for this stream (Upload Flow Control)
                                let (worker_tx, mut worker_rx) = mpsc::channel::<Bytes>(100);
                                stream_worker_txs.insert(frame.header.stream_id, worker_tx);
                                let sem = Arc::new(tokio::sync::Semaphore::new(65535));
                                stream_windows.insert(frame.header.stream_id, sem.clone());
                                
                                let tx_queue_tx = tx_queue_tx.clone();
                                let stream_id = frame.header.stream_id;
                                tokio::spawn(async move {
                                    while let Some(data) = worker_rx.recv().await {
                                        let len = data.len() as u32;
                                        let _ = sem.acquire_many(len).await;
                                        let f = Frame::new(FrameType::Data, 0, stream_id, data);
                                        if tx_queue_tx.send(f).await.is_err() { break; }
                                    }
                                });
                            }
                            FrameType::Data => {
                                if let Some(tx) = self.active_streams.get(&frame.header.stream_id) {
                                    if tx.send(frame.payload).await.is_err() {
                                        self.active_streams.remove(&frame.header.stream_id);
                                        stream_worker_txs.remove(&frame.header.stream_id);
                                    }
                                }
                            }
                            FrameType::StreamClose => {
                                self.active_streams.remove(&frame.header.stream_id);
                                stream_worker_txs.remove(&frame.header.stream_id);
                                stream_windows.remove(&frame.header.stream_id);
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
                            FrameType::Replay => {
                                let local_port = *self.tunnels.values().next().unwrap_or(&3000);
                                let local_addr = format!("127.0.0.1:{}", local_port);
                                let request = frame.payload.clone();
                                tokio::spawn(async move {
                                    let _ = Self::execute_replay(&local_addr, request).await;
                                });
                            }
                            FrameType::UdpData => {
                                // Extract destination port (defaulting to primary tunnel port)
                                let local_port = *self.tunnels.values().next().unwrap_or(&3000);
                                let payload = frame.payload;
                                if payload.len() > 1 {
                                    let addr_len = payload[0] as usize;
                                    let data = payload[1 + addr_len..].to_vec();
                                    
                                    let local_addr = format!("127.0.0.1:{}", local_port);
                                    tokio::spawn(async move {
                                        if let Ok(socket) = tokio::net::UdpSocket::bind("0.0.0.0:0").await {
                                            let _ = socket.send_to(&data, &local_addr).await;
                                        }
                                    });
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

        Ok(())
    }

    async fn spawn_forwarder(&self, stream_id: u32, local_port: u16) -> mpsc::Sender<Bytes> {
        let (tx, mut rx) = mpsc::channel::<Bytes>(100);
        let to_tunnel_tx = self.to_tunnel_tx.clone();
        let close_tx = self.close_stream_tx.clone();
        let local_addr = format!("127.0.0.1:{}", local_port);

        tokio::spawn(async move {
            let local_stream = match TcpStream::connect(&local_addr).await {
                Ok(s) => s,
                Err(_) => {
                    let _ = close_tx.send(stream_id).await;
                    return;
                }
            };
            let (mut local_read, mut local_write) = local_stream.into_split();

            let write_task = tokio::spawn(async move {
                let mut consumed_since_update = 0;
                while let Some(data) = rx.recv().await {
                    let len = data.len();
                    if local_write.write_all(&data).await.is_err() { break; }
                    
                    consumed_since_update += len;
                    if consumed_since_update >= 32768 {
                        let update = Frame::new(FrameType::WindowUpdate, 0, stream_id, Bytes::from((consumed_since_update as u32).to_be_bytes().to_vec()));
                        let _ = to_tunnel_tx.send((0, update.encode())).await; // Using StreamID 0 for control frames or a convention
                        consumed_since_update = 0;
                    }
                }
            });

            let read_task = tokio::spawn(async move {
                let mut buf = [0u8; 4096];
                while let Ok(n) = local_read.read(&mut buf).await {
                    if n == 0 { break; }
                    let _ = to_tunnel_tx.send((stream_id, Bytes::copy_from_slice(&buf[..n]))).await;
                }
            });

            let _ = tokio::join!(read_task, write_task);
            let _ = close_tx.send(stream_id).await;
        });

        tx
    }

    async fn execute_replay(local_addr: &str, raw_request: Bytes) -> Result<()> {
        let mut stream = TcpStream::connect(local_addr).await?;
        stream.write_all(&raw_request).await?;
        Ok(())
    }
}

#[derive(Debug)]
struct NoCertificateVerification;
impl rustls::client::danger::ServerCertVerifier for NoCertificateVerification {
    fn verify_server_cert(&self, _e: &rustls::pki_types::CertificateDer<'_>, _i: &[rustls::pki_types::CertificateDer<'_>], _s: &rustls::pki_types::ServerName<'_>, _o: &[u8], _n: rustls::pki_types::UnixTime) -> Result<rustls::client::danger::ServerCertVerified, rustls::Error> { Ok(rustls::client::danger::ServerCertVerified::assertion()) }
    fn verify_tls12_signature(&self, _m: &[u8], _c: &rustls::pki_types::CertificateDer<'_>, _d: &rustls::DigitallySignedStruct) -> Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> { Ok(rustls::client::danger::HandshakeSignatureValid::assertion()) }
    fn verify_tls13_signature(&self, _m: &[u8], _c: &rustls::pki_types::CertificateDer<'_>, _d: &rustls::DigitallySignedStruct) -> Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> { Ok(rustls::client::danger::HandshakeSignatureValid::assertion()) }
    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> { rustls::crypto::ring::default_provider().signature_verification_algorithms.supported_schemes() }
}
