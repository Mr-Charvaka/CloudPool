use axum::{
    extract::{ws::{Message, WebSocket, WebSocketUpgrade}, Path, State, Extension},
    routing::{get, post},
    Json, Router as AxumRouter,
    response::IntoResponse,
};
use std::sync::Arc;
use serde::Serialize;
use tower_http::cors::CorsLayer;
use rust_embed::RustEmbed;
use axum::http::{header, StatusCode, HeaderValue};
use tracing::{info, error};

use crate::router::{Router, TunnelSnapshot, ControlMessage};
use crate::inspector::{Inspector, CapturedRequest};
use crate::dashboard::{DashboardBroadcaster, DashboardEvent};

#[derive(RustEmbed)]
#[folder = "dashboard/ui/build"]
struct Assets;

#[derive(Serialize)]
pub struct DashboardMetrics {
    pub total_tunnels: usize,
    pub tunnels: Vec<TunnelSnapshot>,
    pub request_history: Vec<CapturedRequest>,
}

pub struct DashboardServer {
    addr: String,
    router: Arc<Router>,
    inspector: Arc<Inspector>,
    broadcaster: Arc<DashboardBroadcaster>,
}

impl DashboardServer {
    pub fn new(
        addr: &str, 
        router: Arc<Router>, 
        inspector: Arc<Inspector>,
        broadcaster: Arc<DashboardBroadcaster>
    ) -> Self {
        Self {
            addr: addr.to_string(),
            router,
            inspector,
            broadcaster,
        }
    }

    pub async fn run(&self) -> anyhow::Result<()> {
        let state = Arc::new(AppState {
            router: self.router.clone(),
            broadcaster: self.broadcaster.clone(),
        });

        let app = AxumRouter::new()
            // WebSocket for real-time updates
            .route("/api/ws", get(ws_handler))
            
            // REST API
            .route("/api/metrics", get(get_metrics))
            .route("/api/tunnels", get(get_tunnels))
            .route("/api/requests", get(get_requests))
            .route("/api/replay/:id", post(replay_request))
            .route("/health", get(health_check))
            
            // Static Dashboard UI (Embedded)
            .fallback(static_handler)
            
            .layer(CorsLayer::permissive())
            .layer(Extension(self.inspector.clone()))
            .with_state(state);

        info!("Integrated Dashboard Server listening on http://{}", self.addr);
        let listener = tokio::net::TcpListener::bind(&self.addr).await?;
        axum::serve(listener, app).await?;
        Ok(())
    }
}

struct AppState {
    router: Arc<Router>,
    broadcaster: Arc<DashboardBroadcaster>,
}

async fn ws_handler(
    ws: WebSocketUpgrade,
    State(state): State<Arc<AppState>>,
) -> impl IntoResponse {
    ws.on_upgrade(|socket| handle_socket(socket, state))
}

async fn handle_socket(mut socket: WebSocket, state: Arc<AppState>) {
    let mut rx = state.broadcaster.subscribe();
    
    info!("New Dashboard UI client connected via WebSocket");

    while let Ok(event) = rx.recv().await {
        let msg = match serde_json::to_string(&event) {
            Ok(s) => Message::Text(s),
            Err(e) => {
                error!("Failed to serialize dashboard event: {:?}", e);
                continue;
            }
        };

        if socket.send(msg).await.is_err() {
            break;
        }
    }
    
    info!("Dashboard UI client disconnected");
}

async fn get_metrics(
    State(state): State<Arc<AppState>>,
    Extension(inspector): Extension<Arc<Inspector>>,
) -> Json<DashboardMetrics> {
    let tunnels = state.router.list_tunnels();
    let snapshots = tunnels.iter().map(|t| t.snapshot()).collect();
    let request_history = inspector.get_history().await;
    
    Json(DashboardMetrics {
        total_tunnels: tunnels.len(),
        tunnels: snapshots,
        request_history,
    })
}

async fn get_tunnels(State(state): State<Arc<AppState>>) -> Json<Vec<TunnelSnapshot>> {
    let tunnels = state.router.list_tunnels().iter().map(|t| t.snapshot()).collect();
    Json(tunnels)
}

async fn get_requests(Extension(inspector): Extension<Arc<Inspector>>) -> Json<Vec<CapturedRequest>> {
    Json(inspector.get_history().await)
}

async fn replay_request(
    State(state): State<Arc<AppState>>,
    Extension(inspector): Extension<Arc<Inspector>>,
    Path(id): Path<String>,
) -> impl IntoResponse {
    if let Some(req) = inspector.get_request_by_id(&id).await {
        if let Some(tunnel) = state.router.get_tunnel(&req.tunnel_id) {
            let msg = ControlMessage::Replay {
                method: req.method,
                path: req.path,
                headers: req.headers,
            };
            let _ = tunnel.control_msg_tx.send(msg).await;
            return "OK";
        }
    }
    "NOT_FOUND"
}

async fn static_handler(uri: axum::http::Uri) -> impl IntoResponse {
    let path = uri.path().trim_start_matches('/');

    if path.is_empty() || path == "index.html" {
        return serve_asset("index.html");
    }

    match serve_asset(path) {
        Ok(body) => Ok(body),
        Err(_) => serve_asset("index.html"), // SPA fallback
    }
}

fn serve_asset(path: &str) -> Result<impl IntoResponse, StatusCode> {
    if let Some(content) = Assets::get(path) {
        let mime = mime_guess::from_path(path).first_or_octet_stream();
        Ok((
            [(header::CONTENT_TYPE, HeaderValue::from_str(mime.as_ref()).unwrap())],
            content.data,
        ))
    } else {
        Err(StatusCode::NOT_FOUND)
    }
}

async fn health_check() -> &'static str {
    "OK"
}
