use anyhow::Result;
use drap_client::connection::ControlConnection;
use drap_client::config::DrapConfig;
use drap_client::display::{TerminalUi, TuiState};
use clap::{Parser, Subcommand};
use tracing::info;
use directories::ProjectDirs;
use std::fs;
use std::path::PathBuf;

#[derive(Parser)]
#[command(name = "drap")]
#[command(about = "D-RAP: Secure Multi-Protocol Tunneling Platform", long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Start one or more tunnels
    Start {
        /// Path to config file
        #[arg(short, long)]
        config: Option<String>,
        /// Local port for quick tunnel
        port: Option<u16>,
        /// Subdomain for quick tunnel
        subdomain: Option<String>,
    },
    /// Log in to the D-RAP relay server
    Login {
        /// Auth token from the D-RAP dashboard
        token: String,
    },
    /// List active tunnels on the relay
    List,
    /// Check server status and latency
    Status,
}

fn get_config_dir() -> PathBuf {
    ProjectDirs::from("com", "drap", "cli")
        .map(|d| d.config_dir().to_path_buf())
        .unwrap_or_else(|| PathBuf::from("."))
}

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt::init();
    let cli = Cli::parse();

    rustls::crypto::ring::default_provider()
        .install_default()
        .expect("Failed to install rustls crypto provider");

    match cli.command {
        Commands::Start { config, port, subdomain } => {
            let config = if let Some(path) = config {
                info!("Loading configuration from {}", path);
                DrapConfig::from_file(&path)?
            } else if let Some(p) = port {
                DrapConfig::default_for_port(p, subdomain)
            } else {
                return Err(anyhow::anyhow!("Either --config or a local port must be specified"));
            };

            info!("Starting D-RAP CLI Client (Control Region: {})", config.relay_host);
            
            let mut backoff = std::time::Duration::from_secs(1);
            loop {
                let connection_res = async {
                    let mut connection = ControlConnection::new(&format!("{}:4443", config.relay_host)).await?;
                    connection.perform_handshake().await?;

                    for (name, tunnel) in &config.tunnels {
                        info!("Requesting tunnel [{}] for 127.0.0.1:{} (Proto: {})", 
                            name, tunnel.local_port, tunnel.proto);
                        connection.create_tunnel(tunnel).await?;
                    }

                    // Start TUI (Optional: only if not already started)
                    // For now, let's keep it simple and just run the connection.
                    connection.run().await?;
                    Ok::<(), anyhow::Error>(())
                }.await;

                if let Err(e) = connection_res {
                    error!("Tunnel connection error: {:?}. Retrying in {:?}...", e, backoff);
                    tokio::time::sleep(backoff).await;
                    backoff = (backoff * 2).min(std::time::Duration::from_secs(60));
                } else {
                    // If it returned Ok, it might be a clean exit, or we reset backoff
                    backoff = std::time::Duration::from_secs(1);
                }
            }
        }
        Commands::Login { token } => {
            let config_dir = get_config_dir();
            fs::create_dir_all(&config_dir)?;
            fs::write(config_dir.join("token"), token)?;
            info!("Successfully logged in! Token saved to {:?}", config_dir);
        }
        Commands::List => {
            let client = reqwest::Client::new();
            let res = client.get("http://localhost:4000/api/tunnels").send().await?;
            let tunnels: serde_json::Value = res.json().await?;
            println!("{:<20} | {:<10} | {:<10}", "Subdomain", "Sent", "Recv");
            println!("{}", "-".repeat(46));
            if let Some(arr) = tunnels.as_array() {
                for t in arr {
                    println!("{:<20} | {:<10} | {:<10}", 
                        t["subdomain"].as_str().unwrap_or("?"),
                        t["bytes_sent"],
                        t["bytes_recv"]
                    );
                }
            }
        }
        Commands::Status => {
            let client = reqwest::Client::new();
            let res = client.get("http://localhost:4000/api/metrics").send().await?;
            let metrics: serde_json::Value = res.json().await?;
            println!("D-RAP Relay Status");
            println!("Relay Host:    {}", metrics["total_tunnels"]);
            println!("Total Tunnels: {}", metrics["total_tunnels"]);
            println!("API Health:    OK");
        }
    }

    Ok(())
}
