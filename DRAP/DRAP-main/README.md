# D-RAP (Distributed Remote Access Protocol) v1.0

D-RAP is a high-performance tunneling platform built with **Rust**, **Go**, and **SvelteKit**. It allows you to expose local services to the internet securely via a unified relay server with a premium real-time dashboard.

## Features
- 🚀 **High Performance:** Core engine written in asynchronous Rust (Tokio).
- 🔐 **Secure by Default:** TLS 1.3 encryption with `rustls`.
- 📊 **Real-time Inspector:** Monitor Method, Path, and full Header sets for every HTTP request.
- 💾 **PostgreSQL Persistence:** Tunnel assignments and traffic history survive restarts.
- 📑 **Multi-Tunnel Support:** Deploy entire service meshes with a single `drap.yml` config.
- 🎨 **Glassmorphism Dashboard:** Dark-mode visual interface with live bandwidth gauges.
- 🐳 **Docker Ready:** Complete `docker-compose` stack for production deployments.

## Quick Start (CLI)
Expose a local web server on port 3000:
```bash
drap-client 3000 portal
```

## Quick Start (Multi-Tunnel)
Define your services in `drap.yml`:
```yaml
relay_host: "empirebot.in"
tunnels:
  - local_port: 3000
    subdomain: "web"
  - local_port: 8000
    subdomain: "api"
```
Then start them all:
```bash
drap-client --config drap.yml
```

## Dashboard
Access the command center to inspect traffic and manage tunnels in real-time.
- **Overview:** Bandwidth counters and active stream status.
- **Traffic Log:** Deep inspection of HTTP headers and request metadata.

---
Built by Grit, Passion & Love.
