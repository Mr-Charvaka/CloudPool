use clap::{Parser, Subcommand};
use serde_json::{json, Value};
use std::error::Error;
use std::path::Path;
use reqwest::multipart;

use cloudpool_rust::config::CliConfig;
use cloudpool_rust::crypto::CryptoUtil;

#[derive(Parser)]
#[command(name = "cloudpool")]
#[command(about = "CloudPool High-Performance CLI & Encryption Service", version = "0.1.0")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Authenticate with CloudPool and save JWT session token
    Login {
        /// CloudPool server API base URL (default: http://localhost:8080/api)
        #[arg(long, default_value = "http://localhost:8080/api")]
        url: String,
        /// Developer login email
        #[arg(short, long)]
        email: String,
        /// Developer login password
        #[arg(short, long)]
        password: String,
    },
    /// Upload a file to a bucket (optionally encrypting it using GCM)
    Upload {
        /// Path to the local file to upload
        file_path: String,
        /// Target storage bucket/pool name (default: default-pool)
        #[arg(short, long, default_value = "default-pool")]
        bucket: String,
        /// Locally encrypt file contents with AES-GCM before upload
        #[arg(short, long)]
        encrypt: bool,
    },
    /// Download a file (optionally decrypting it using GCM/ECB fallback)
    Download {
        /// File Metadata ID (UUID)
        file_id: String,
        /// Target local output path
        output_path: String,
        /// Decrypt file contents locally after download
        #[arg(short, long)]
        decrypt: bool,
    },
    /// Database table orchestration operations
    Db {
        #[command(subcommand)]
        command: DbCommands,
    },
    /// Run real-time semantic search over uploaded files
    Search {
        /// The query string
        query: String,
    },
}

#[derive(Subcommand)]
enum DbCommands {
    /// Provision a custom relational table
    CreateTable {
        /// Table name (system internal)
        name: String,
        /// Display name (UI friendly)
        display_name: String,
        /// Description of the table
        description: String,
        /// Table fields list as a JSON array (e.g. '[{"fieldName":"id","fieldType":"VARCHAR","required":true}]')
        fields_json: String,
    },
    /// Query all records inside a custom relational table
    Query {
        /// Table ID (UUID)
        table_id: String,
    },
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    let cli = Cli::parse();
    
    let master_key = std::env::var("CLOUDPOOL_ENCRYPTION_MASTER_KEY")
        .map_err(|_| "CLOUDPOOL_ENCRYPTION_MASTER_KEY must be set to a 64-character hex string".to_string())?;

    if master_key.len() != 64 || !master_key.chars().all(|c| c.is_ascii_hexdigit()) {
        return Err("CLOUDPOOL_ENCRYPTION_MASTER_KEY must be a 64-character hex string (32 bytes)".into());
    }

    match cli.command {
        Commands::Login { url, email, password } => {
            println!("Attempting login to {}...", url);
            let client = reqwest::Client::new();
            
            let login_payload = json!({
                "email": email,
                "password": password
            });

            let res = client.post(format!("{}/auth/login", url.trim_end_matches('/')))
                .json(&login_payload)
                .send()
                .await?;

            if !res.status().is_success() {
                let err_text = res.text().await?;
                return Err(format!("Login failed: {}", err_text).into());
            }

            let res_json: Value = res.json().await?;
            let token = res_json.get("token")
                .and_then(|t| t.as_str())
                .ok_or("No token returned in login response")?;

            let mut config = CliConfig::load();
            config.base_url = url;
            config.jwt_token = Some(token.to_string());
            config.username = Some(email.clone());
            config.save()?;

            println!("✅ Login successful! Session token saved for {}.", email);
        }

        Commands::Upload { file_path, bucket, encrypt } => {
            let config = CliConfig::load();
            let token = config.jwt_token.as_ref().ok_or("Unauthorized. Please run 'cloudpool login' first.")?;
            
            let path = Path::new(&file_path);
            if !path.exists() {
                return Err(format!("Local file not found: {}", file_path).into());
            }

            let file_name = path.file_name().and_then(|n| n.to_str()).unwrap_or("file");
            let mut file_bytes = tokio::fs::read(&file_path).await?;
            
            if encrypt {
                println!("🔒 Encrypting file content using AES-GCM...");
                let crypto = CryptoUtil::new(&master_key);
                file_bytes = crypto.encrypt_bytes(&file_bytes)?;
            }

            println!("Uploading '{}' ({} bytes) to bucket '{}'...", file_name, file_bytes.len(), bucket);
            let client = reqwest::Client::new();
            
            let file_part = multipart::Part::bytes(file_bytes)
                .file_name(file_name.to_string());

            let form = multipart::Form::new()
                .part("file", file_part)
                .text("bucket", bucket);

            let res = client.post(format!("{}/files/upload", config.base_url.trim_end_matches('/')))
                .header("Authorization", format!("Bearer {}", token))
                .multipart(form)
                .send()
                .await?;

            if !res.status().is_success() {
                let err_text = res.text().await?;
                return Err(format!("Upload failed: {}", err_text).into());
            }

            let metadata: Value = res.json().await?;
            println!("✅ Upload complete!\nMetadata:\n{}", serde_json::to_string_pretty(&metadata)?);
        }

        Commands::Download { file_id, output_path, decrypt } => {
            let config = CliConfig::load();
            let token = config.jwt_token.as_ref().ok_or("Unauthorized. Please run 'cloudpool login' first.")?;

            println!("Downloading file ID {}...", file_id);
            let client = reqwest::Client::new();

            let res = client.get(format!("{}/files/download/{}", config.base_url.trim_end_matches('/'), file_id))
                .header("Authorization", format!("Bearer {}", token))
                .send()
                .await?;

            if !res.status().is_success() {
                let err_text = res.text().await?;
                return Err(format!("Download failed: {}", err_text).into());
            }

            let mut file_bytes = res.bytes().await?.to_vec();

            if decrypt {
                println!("🔓 Decrypting file content using AES-GCM...");
                let crypto = CryptoUtil::new(&master_key);
                file_bytes = crypto.decrypt_bytes(&file_bytes)?;
            }

            tokio::fs::write(&output_path, file_bytes).await?;
            println!("✅ Download and save complete to '{}'!", output_path);
        }

        Commands::Search { query } => {
            let config = CliConfig::load();
            let token = config.jwt_token.as_ref().ok_or("Unauthorized. Please run 'cloudpool login' first.")?;

            println!("Executing semantic search for query: '{}'...", query);
            let client = reqwest::Client::new();

            let res = client.get(format!("{}/vector/search", config.base_url.trim_end_matches('/')))
                .header("Authorization", format!("Bearer {}", token))
                .query(&[("q", &query)])
                .send()
                .await?;

            if !res.status().is_success() {
                let err_text = res.text().await?;
                return Err(format!("Search failed: {}", err_text).into());
            }

            let results: Value = res.json().await?;
            println!("🔍 Search Results:\n{}", serde_json::to_string_pretty(&results)?);
        }

        Commands::Db { command } => {
            let config = CliConfig::load();
            let token = config.jwt_token.as_ref().ok_or("Unauthorized. Please run 'cloudpool login' first.")?;
            let client = reqwest::Client::new();

            match command {
                DbCommands::CreateTable { name, display_name, description, fields_json } => {
                    let fields: Value = serde_json::from_str(&fields_json)?;
                    let payload = json!({
                        "name": name,
                        "displayName": display_name,
                        "description": description,
                        "fields": fields
                    });

                    println!("Provisioning custom relational table '{}'...", name);
                    let res = client.post(format!("{}/v1/db/tables", config.base_url.trim_end_matches('/')))
                        .header("Authorization", format!("Bearer {}", token))
                        .json(&payload)
                        .send()
                        .await?;

                    if !res.status().is_success() {
                        let err_text = res.text().await?;
                        return Err(format!("Table creation failed: {}", err_text).into());
                    }

                    let table: Value = res.json().await?;
                    println!("✅ Table provisioned successfully!\n{}", serde_json::to_string_pretty(&table)?);
                }

                DbCommands::Query { table_id } => {
                    println!("Querying records for table ID {}...", table_id);
                    let res = client.get(format!("{}/v1/db/tables/{}/records", config.base_url.trim_end_matches('/'), table_id))
                        .header("Authorization", format!("Bearer {}", token))
                        .send()
                        .await?;

                    if !res.status().is_success() {
                        let err_text = res.text().await?;
                        return Err(format!("Query failed: {}", err_text).into());
                    }

                    let records: Value = res.json().await?;
                    println!("📊 Records Query Output:\n{}", serde_json::to_string_pretty(&records)?);
                }
            }
        }
    }

    Ok(())
}
