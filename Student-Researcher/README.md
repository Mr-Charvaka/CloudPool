# CloudPool for Students & Researchers

**Deploy your projects to the internet in one command. No AWS. No credit card.**

```bash
cloudpool deploy    # Your local project → yourname.cloudpool.dev
```

## What you get

| Feature | What it does |
|---|---|
| **Tunnel (DRAP)** | Expose localhost to the internet with custom subdomains |
| **Auth API** | Login, register, API keys for your projects |
| **File Storage** | Upload/download files, share via token links |
| **Database API** | Create tables, CRUD records via REST/GraphQL |
| **Vector Search** | Semantic search for AI projects (Weaviate + OpenAI) |
| **Email Sandbox** | Test email sending, catch incoming emails |
| **Dashboard** | Web UI to manage everything |

## Quick start

```bash
# Start infrastructure
docker compose up -d

# Build (student profile — lighter, faster)
cd backend/spring-boot
mvn clean install -P student

# Start backend
mvn spring-boot:run -pl cloudpool-gateway

# Start tunnel
cd ../../Student-Researcher/drap
cargo run -- http 8080
```

## How it's different from Enterprise

No payments, no Kubernetes, no S3, no monitoring stack. Just you, your code, and the internet.
