use anyhow::Result;
use drap_common::tls;
use drap_server::control_server::ControlServer;
use drap_server::data_server::DataServer;
use drap_server::dashboard_server::DashboardServer;
use drap_server::dashboard::DashboardBroadcaster;
use drap_server::router::Router;
use drap_server::inspector::Inspector;
use drap_server::db::Database;
use std::path::Path;
use std::sync::Arc;
use tokio_rustls::rustls;
use tracing::{info, warn};
use tracing_subscriber;

#[tokio::main]
async fn main() -> Result<()> {
    // 0. Initialize Logging (JSON in production, human-readable in dev)
    let is_prod = std::env::var("APP_ENV").unwrap_or_default() == "production";
    if is_prod {
        tracing_subscriber::fmt().json().init();
    } else {
        tracing_subscriber::fmt::init();
    }

    rustls::crypto::ring::default_provider()
        .install_default()
        .expect("Failed to install rustls crypto provider");

    info!("Starting D-RAP Relay Server (Domain: empirebot.in)...");

    // 1. Initialize Persistence (Optional but recommended)
    let db = if let Ok(db_url) = std::env::var("DATABASE_URL") {
        match Database::new(&db_url).await {
            Ok(pool) => {
                info!("PostgreSQL connection established");
                Some(Arc::new(pool))
            }
            Err(e) => {
                warn!("PostgreSQL enabled but connection failed: {:?}", e);
                None
            }
        }
    } else {
        warn!("DATABASE_URL not set, running in volatile mode");
        None
    };

    // 2. Initialize the Global Dashboard Broadcaster
    let broadcaster = Arc::new(DashboardBroadcaster::new());

    // 3. Initialize the Router and Inspector
    let router = Arc::new(Router::new("empirebot.in", db.clone(), broadcaster.clone()));
    let inspector = Arc::new(Inspector::new(500, db, broadcaster.clone())); 

    // 3. Load TLS certificates
    let cert_path = Path::new("certs/cert.pem");
    let key_path = Path::new("certs/key.pem");

    let certs = tls::load_certs(cert_path)?;
    let key = tls::load_private_key(key_path)?;

    // 4. Build TLS configuration
    let mut server_config = rustls::ServerConfig::builder()
        .with_no_client_auth()
        .with_single_cert(certs, key)
        .map_err(|e| anyhow::anyhow!("Failed to create TLS config: {}", e))?;
    
    server_config.alpn_protocols = vec![b"drap/1".to_vec()];

    // 5. Start Data Server (Public Traffic) in the background
    let data_router = router.clone();
    let data_inspector = inspector.clone();
    tokio::spawn(async move {
        let data_server = DataServer::new("0.0.0.0:8081", data_router, data_inspector);
        if let Err(e) = data_server.run().await {
            tracing::error!("Data server error: {:?}", e);
        }
    });

    // 6. Start Integrated Dashboard Server in the background
    let dashboard_router = router.clone();
    let dashboard_inspector = inspector.clone();
    let dashboard_broadcaster = broadcaster.clone();
    tokio::spawn(async move {
        let dashboard_server = DashboardServer::new(
            "0.0.0.0:4000", 
            dashboard_router, 
            dashboard_inspector,
            dashboard_broadcaster
        );
        if let Err(e) = dashboard_server.run().await {
            tracing::error!("Dashboard server error: {:?}", e);
        }
    });

    // 7. Start Control Server (Tunneling Protocol) - blocked
    let control_router = router.clone();
    let control_server = ControlServer::new(server_config, "0.0.0.0:4443", control_router.clone());
    
    // Graceful Shutdown Handler
    tokio::select! {
        res = control_server.run() => {
            if let Err(e) = res { tracing::error!("Control server error: {:?}", e); }
        }
        _ = tokio::signal::ctrl_c() => {
            info!("Shutdown signal received. Draining tunnels...");
            control_router.broadcast_goaway("Server shutting down for maintenance").await;
            tokio::time::sleep(tokio::time::Duration::from_secs(1)).await;
        }
    }

    Ok(())
}
