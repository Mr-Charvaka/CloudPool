
# ==============================================================
# DRAP Re-Push Script — individual commit per file/group
# Resets the big bundled DRAP commit and re-commits each file
# with a descriptive message, then force-pushes to origin/main
# ==============================================================

Set-Location "d:\D\RESUME PROJECTS\Cloud Pool"

Write-Host "==> Resetting last bundled DRAP commit (keeping files on disk)..." -ForegroundColor Cyan
git reset HEAD~1

Write-Host "`n==> Staging and committing DRAP root workspace files..." -ForegroundColor Cyan

git add "DRAP/.gitignore"
git commit -m "chore(drap): add gitignore rules to exclude Rust build artifacts and local secrets from version control"

git add "DRAP/Cargo.toml"
git commit -m "build(drap): define DRAP workspace Cargo.toml declaring all member crates (client, server, protocol, common)"

git add "DRAP/Cargo.lock"
git commit -m "build(drap): lock exact crate dependency versions for reproducible DRAP builds"

git add "DRAP/README.md"
git commit -m "docs(drap): add DRAP README with architecture overview, quick-start, and configuration reference"

git add "DRAP/drap.yml"
git commit -m "config(drap): add production tunnel configuration targeting empirebot.in relay on port 4443"

git add "DRAP/drap_local.yml"
git commit -m "config(drap): add local development tunnel configuration for testing against localhost relay"

git add "DRAP/init.sql"
git commit -m "db(drap): add SQL schema for tunnel registry, session history, and token authentication tables"

git add "DRAP/later.txt"
git commit -m "chore(drap): add developer notes tracking future improvements and known limitations"

git add "DRAP/DARP.txt"
git commit -m "docs(drap): add DARP protocol specification documenting frame structure, handshake, and message flow"

git add "DRAP/DRAP.docx"
git commit -m "docs(drap): add DRAP architecture Word document with system design diagrams and component breakdown"

# ---- Certificates ----
Write-Host "`n==> Committing TLS certificates..." -ForegroundColor Cyan

git add "DRAP/certs/cert.pem"
git commit -m "security(drap): add self-signed TLS certificate for encrypted client-server tunnel connections"

git add "DRAP/certs/key.pem"
git commit -m "security(drap): add TLS private key paired with cert.pem for server-side encryption"

# ---- drap-common ----
Write-Host "`n==> Committing drap-common shared library crate..." -ForegroundColor Cyan

git add "DRAP/crates/drap-common/.gitignore"
git commit -m "chore(drap-common): add gitignore to exclude compiled artifacts from shared library crate"

git add "DRAP/crates/drap-common/Cargo.toml"
git commit -m "build(drap-common): define shared utility crate with tokio-rustls and rcgen as core dependencies"

git add "DRAP/crates/drap-common/src/lib.rs"
git commit -m "feat(drap-common): implement shared utility re-exports and error types used by all DRAP crates"

git add "DRAP/crates/drap-common/src/tls.rs"
git commit -m "feat(drap-common): implement TLS configuration builder for both client and server encrypted connections"

# ---- drap-protocol ----
Write-Host "`n==> Committing drap-protocol codec crate..." -ForegroundColor Cyan

git add "DRAP/crates/drap-protocol/.gitignore"
git commit -m "chore(drap-protocol): add gitignore to exclude compiled artifacts from protocol crate"

git add "DRAP/crates/drap-protocol/Cargo.toml"
git commit -m "build(drap-protocol): define protocol codec crate with tokio, serde, and bincode as dependencies"

git add "DRAP/crates/drap-protocol/src/lib.rs"
git commit -m "feat(drap-protocol): implement core DRAP protocol message types, serialization, and public API"

git add "DRAP/crates/drap-protocol/src/codec.rs"
git commit -m "feat(drap-protocol): implement async length-prefixed frame codec for encoding and decoding DRAP messages"

git add "DRAP/crates/drap-protocol/src/frame_type.rs"
git commit -m "feat(drap-protocol): define FrameType enum distinguishing control, data, and heartbeat channel messages"

# ---- drap-client ----
Write-Host "`n==> Committing drap-client binary crate..." -ForegroundColor Cyan

git add "DRAP/crates/drap-client/.gitignore"
git commit -m "chore(drap-client): add gitignore to exclude compiled binary and local config overrides"

git add "DRAP/crates/drap-client/Cargo.toml"
git commit -m "build(drap-client): define DRAP client binary with tokio-rustls, clap, and serde-yaml dependencies"

git add "DRAP/crates/drap-client/src/lib.rs"
git commit -m "feat(drap-client): expose client module public API for library consumers"

git add "DRAP/crates/drap-client/src/config.rs"
git commit -m "feat(drap-client): implement YAML config loader reading server address, port, and tunnel options"

git add "DRAP/crates/drap-client/src/connection.rs"
git commit -m "feat(drap-client): implement TLS tunnel connection manager with exponential backoff auto-reconnect"

git add "DRAP/crates/drap-client/src/display.rs"
git commit -m "feat(drap-client): implement terminal UI showing live connection status and assigned tunnel URL"

git add "DRAP/crates/drap-client/src/main.rs"
git commit -m "feat(drap-client): implement CLI entry point with argument parsing and async tunnel bootstrap"

# ---- drap-server ----
Write-Host "`n==> Committing drap-server relay binary crate..." -ForegroundColor Cyan

git add "DRAP/crates/drap-server/.gitignore"
git commit -m "chore(drap-server): add gitignore to exclude compiled server binary and runtime log files"

git add "DRAP/crates/drap-server/Cargo.toml"
git commit -m "build(drap-server): define relay server binary with axum, tokio-rustls, sqlx, and dashmap dependencies"

git add "DRAP/crates/drap-server/src/lib.rs"
git commit -m "feat(drap-server): expose server module public API and top-level component re-exports"

git add "DRAP/crates/drap-server/src/main.rs"
git commit -m "feat(drap-server): implement server entry point bootstrapping control, data, and dashboard servers concurrently"

git add "DRAP/crates/drap-server/src/router.rs"
git commit -m "feat(drap-server): implement subdomain-based tunnel router dispatching HTTP requests to registered clients"

git add "DRAP/crates/drap-server/src/control_server.rs"
git commit -m "feat(drap-server): implement TLS control server handling client registration, heartbeat, and tunnel teardown"

git add "DRAP/crates/drap-server/src/data_server.rs"
git commit -m "feat(drap-server): implement HTTP proxy data server forwarding public requests through active tunnels"

git add "DRAP/crates/drap-server/src/dashboard.rs"
git commit -m "feat(drap-server): implement HTML renderer generating embedded tunnel status dashboard page"

git add "DRAP/crates/drap-server/src/dashboard_server.rs"
git commit -m "feat(drap-server): implement axum-based dashboard REST server exposing tunnel metrics and management API"

git add "DRAP/crates/drap-server/src/db.rs"
git commit -m "feat(drap-server): implement SQLite persistence layer for tunnel registry, session log, and auth tokens"

git add "DRAP/crates/drap-server/src/error_pages.rs"
git commit -m "feat(drap-server): implement custom HTML error pages for 404 not-found and tunnel-unavailable states"

git add "DRAP/crates/drap-server/src/inspector.rs"
git commit -m "feat(drap-server): implement HTTP request-response inspector for real-time tunnel traffic debugging"

git add "DRAP/crates/drap-server/src/subdomain_gen.rs"
git commit -m "feat(drap-server): implement collision-resistant random subdomain generator for unique tunnel URL assignment"

git add "DRAP/crates/drap-server/src/tcp_server.rs"
git commit -m "feat(drap-server): implement raw TCP relay server for forwarding non-HTTP protocol connections through tunnels"

git add "DRAP/crates/drap-server/src/udp_server.rs"
git commit -m "feat(drap-server): implement UDP datagram relay for tunneling UDP-based protocols like DNS and QUIC"

git add "DRAP/crates/drap-server/src/bin/gen_certs.rs"
git commit -m "feat(drap-server): add gen_certs binary to generate self-signed TLS certificate+key pairs for development"

git add "DRAP/crates/drap-server/src/security/mod.rs"
git commit -m "feat(drap-server): expose security sub-module housing rate limiter and abuse prevention utilities"

git add "DRAP/crates/drap-server/src/security/rate_limiter.rs"
git commit -m "feat(drap-server): implement token-bucket rate limiter per tunnel to prevent abuse and mitigate DDoS attacks"

# ---- Dashboard API (Go) ----
Write-Host "`n==> Committing DRAP dashboard Go API backend..." -ForegroundColor Cyan

git add "DRAP/dashboard/api/go.mod"
git commit -m "build(drap-dashboard): initialize Go module manifest for the DRAP dashboard REST API backend"

git add "DRAP/dashboard/api/go.sum"
git commit -m "build(drap-dashboard): lock Go dependency checksums for tamper-proof dashboard API builds"

git add "DRAP/dashboard/api/main.go"
git commit -m "feat(drap-dashboard): implement Go HTTP REST server exposing tunnel count, stats, and management endpoints"

# ---- Dashboard UI (SvelteKit) ----
Write-Host "`n==> Committing DRAP dashboard SvelteKit frontend..." -ForegroundColor Cyan

git add "DRAP/dashboard/ui/.gitignore"
git commit -m "chore(drap-dashboard-ui): add gitignore excluding SvelteKit build output, node_modules, and env files"

git add "DRAP/dashboard/ui/.npmrc"
git commit -m "config(drap-dashboard-ui): configure npm registry and engine strictness for dashboard UI"

git add "DRAP/dashboard/ui/README.md"
git commit -m "docs(drap-dashboard-ui): add SvelteKit dashboard UI README with setup, dev server, and build instructions"

git add "DRAP/dashboard/ui/package.json"
git commit -m "build(drap-dashboard-ui): define SvelteKit project with svelte, vite, and typescript dev dependencies"

git add "DRAP/dashboard/ui/package-lock.json"
git commit -m "build(drap-dashboard-ui): lock npm dependency tree for reproducible dashboard UI installs"

git add "DRAP/dashboard/ui/svelte.config.js"
git commit -m "config(drap-dashboard-ui): configure SvelteKit static adapter and preprocessors for dashboard build"

git add "DRAP/dashboard/ui/vite.config.ts"
git commit -m "config(drap-dashboard-ui): configure Vite bundler with SvelteKit plugin for dashboard frontend"

git add "DRAP/dashboard/ui/tsconfig.json"
git commit -m "config(drap-dashboard-ui): configure TypeScript compiler with strict mode and SvelteKit path aliases"

git add "DRAP/dashboard/ui/src/app.html"
git commit -m "feat(drap-dashboard-ui): add SvelteKit root HTML shell template with meta tags and body placeholder"

git add "DRAP/dashboard/ui/src/app.d.ts"
git commit -m "feat(drap-dashboard-ui): add TypeScript global type declarations for SvelteKit App namespace"

git add "DRAP/dashboard/ui/src/lib/index.ts"
git commit -m "feat(drap-dashboard-ui): add lib barrel file exporting shared dashboard utility functions"

git add "DRAP/dashboard/ui/src/lib/assets/favicon.svg"
git commit -m "feat(drap-dashboard-ui): add SVG favicon for the DRAP web dashboard"

git add "DRAP/dashboard/ui/src/routes/+layout.svelte"
git commit -m "feat(drap-dashboard-ui): add root SvelteKit layout with global navigation and shared UI shell"

git add "DRAP/dashboard/ui/src/routes/+layout.ts"
git commit -m "feat(drap-dashboard-ui): configure SSR disabled and client-only rendering for dashboard layout"

git add "DRAP/dashboard/ui/src/routes/+page.svelte"
git commit -m "feat(drap-dashboard-ui): implement main dashboard page with live tunnel list, status badges, and metrics"

git add "DRAP/dashboard/ui/src/routes/layout.css"
git commit -m "style(drap-dashboard-ui): add global layout CSS for dashboard typography, spacing, and color scheme"

git add "DRAP/dashboard/ui/static/robots.txt"
git commit -m "config(drap-dashboard-ui): add robots.txt disallowing search engine indexing of internal dashboard"

# ---- Deploy ----
Write-Host "`n==> Committing DRAP deployment configuration..." -ForegroundColor Cyan

git add "DRAP/deploy/docker-compose.yml"
git commit -m "infra(drap): add Docker Compose stack deploying DRAP relay server with PostgreSQL and volume mounts"

# ---- DRAP-main reference snapshot ----
Write-Host "`n==> Committing DRAP-main reference directory..." -ForegroundColor Cyan

git add "DRAP/DRAP-main/.gitignore"
git commit -m "chore(drap-main): add gitignore for upstream reference snapshot build artifacts"

git add "DRAP/DRAP-main/Cargo.toml"
git commit -m "build(drap-main): upstream reference workspace Cargo.toml for cross-diffing against local DRAP"

git add "DRAP/DRAP-main/DARP.txt"
git commit -m "docs(drap-main): upstream DARP protocol spec snapshot for reference comparison"

git add "DRAP/DRAP-main/DRAP.docx"
git commit -m "docs(drap-main): upstream DRAP architecture Word document reference snapshot"

git add "DRAP/DRAP-main/README.md"
git commit -m "docs(drap-main): upstream DRAP README reference for comparing documentation drift"

git add "DRAP/DRAP-main/drap.yml"
git commit -m "config(drap-main): upstream production tunnel configuration reference snapshot"

git add "DRAP/DRAP-main/init.sql"
git commit -m "db(drap-main): upstream SQL schema snapshot for cross-referencing schema evolution"

git add "DRAP/DRAP-main/later.txt"
git commit -m "chore(drap-main): upstream developer notes snapshot for tracking upstream changes"

git add "DRAP/DRAP-main/certs/"
git commit -m "security(drap-main): upstream reference TLS certificates for development environment parity"

git add "DRAP/DRAP-main/crates/drap-common/"
git commit -m "feat(drap-main): upstream drap-common shared utilities reference snapshot"

git add "DRAP/DRAP-main/crates/drap-protocol/"
git commit -m "feat(drap-main): upstream drap-protocol frame codec reference snapshot"

git add "DRAP/DRAP-main/crates/drap-client/"
git commit -m "feat(drap-main): upstream drap-client tunnel client reference snapshot"

git add "DRAP/DRAP-main/crates/drap-server/"
git commit -m "feat(drap-main): upstream drap-server relay server reference snapshot"

git add "DRAP/DRAP-main/dashboard/api/"
git commit -m "feat(drap-main): upstream dashboard Go REST API reference snapshot"

git add "DRAP/DRAP-main/dashboard/ui/"
git commit -m "feat(drap-main): upstream dashboard SvelteKit UI reference snapshot"

git add "DRAP/DRAP-main/deploy/"
git commit -m "infra(drap-main): upstream Docker Compose deployment reference snapshot"

# ---- Force push ----
Write-Host "`n==> Force-pushing rewritten history to origin/main..." -ForegroundColor Yellow
git push --force-with-lease origin main

Write-Host "`n✅ Done! All DRAP files are now pushed with individual descriptive commits." -ForegroundColor Green
git log --oneline -70
