# Contributing to CloudPool

Thank you for your interest in contributing to CloudPool!

CloudPool is a **polyglot project** — you don't need to know Spring Boot or Rust to contribute meaningfully.
Every skill set has a home here. Pick the track that matches your background.

---

## 🗺️ Contribution Tracks by Tech Stack

### ⚛️ MERN / React / Node.js Developers
**You own the entire frontend and SDK ecosystem.**

| Area | Tech | Description |
|------|------|-------------|
| [`/frontend/react-dashboard`](./frontend/react-dashboard) | React + TypeScript | Rebuild the admin dashboard as a modern React SPA |
| [`/sdk/javascript`](./sdk/javascript) | TypeScript/Node.js | Official JS/TS client SDK for CloudPool APIs |
| [`/cli`](./cli) | Node.js (Commander.js) | `cloudpool` CLI tool — `init`, `deploy`, `tunnel` commands |
| [`/storage-adapters/s3`](./storage-adapters/s3) | Node.js (Express) | S3-compatible storage microservice |
| [`/DRAP/dashboard/ui`](./DRAP/dashboard/ui) | SvelteKit (or React) | DRAP tunnel monitoring dashboard |

**Quick start:**
```bash
# React Dashboard
cd frontend/react-dashboard
npm install && npm run dev

# JavaScript SDK
cd sdk/javascript
npm install && npm test

# CLI
cd cli
npm install && node bin/cloudpool.js --help
```

---

### 🐍 Python Developers
**You own the AI/ML layer and data pipelines.**

| Area | Tech | Description |
|------|------|-------------|
| [`/sdk/python`](./sdk/python) | Python | Official Python client SDK |
| [`/workers/embedding-worker`](./workers/embedding-worker) | Python (FastAPI) | OpenAI embedding generation microservice |
| [`/workers/file-processor`](./workers/file-processor) | Python | Document parsing, OCR, PDF text extraction |
| Data pipeline scripts | Python | Bulk data import/export utilities |

**Quick start:**
```bash
cd sdk/python
pip install -e ".[dev]"
pytest
```

---

### 🐹 Go Developers
**You own the infrastructure tooling and the DRAP dashboard API.**

| Area | Tech | Description |
|------|------|-------------|
| [`/DRAP/dashboard/api`](./DRAP/dashboard/api) | Go | REST API for DRAP tunnel dashboard — already Go! |
| [`/storage-adapters/s3`](./storage-adapters/s3) | Go | S3-compatible storage proxy |
| [`/cli`](./cli) | Go (Cobra) | Alternative Go CLI implementation |
| Infrastructure tooling | Go | Health check probes, metrics exporters |

**Quick start:**
```bash
cd DRAP/dashboard/api
go mod download
go run main.go
```

---

### ☕ Spring Boot / Java Developers
**You own the core orchestration engine.**

| Area | Tech | Description |
|------|------|-------------|
| [`/backend/spring-boot`](./backend/spring-boot) | Java 21, Spring Boot 3 | Core API — DB provisioning, auth, vector search, compute |
| Real serverless sandbox | Java + GraalVM Isolates | Replace the Nashorn sandbox with real isolation |
| S3 storage adapter | Java (Spring) | Plug in AWS S3 / Cloudflare R2 / MinIO |
| GraphQL schema extensions | Java | Add new GraphQL resolvers and subscriptions |

**Quick start (compile and run specific microservices):**
```bash
# 1. Compile and install all submodules
cd backend/spring-boot
mvn clean install -DskipTests

# 2. Run a specific service (e.g. Gateway, Auth, or Data) using the project flag (-pl)
# Use the 'local' profile to run standalone without requiring Redis/RabbitMQ containers
mvn -pl cloudpool-gateway spring-boot:run -Dspring-boot.run.profiles=local
```

---

### 🦀 Rust Developers
**You own the performance layer and the DRAP tunnel.**

| Area | Tech | Description |
|------|------|-------------|
| [`/backend/rust`](./backend/rust) | Rust + JNI | Native performance layer — compression, hashing, CSV, WebP |
| [`/DRAP/crates`](./DRAP/crates) | Rust (tokio, axum) | DRAP tunnel server — control plane, data plane, router |
| WASM serverless runtime | Rust (wasmtime) | Real sandboxed serverless function execution |
| Native vector ops | Rust + SIMD | AVX2/NEON cosine similarity, HNSW index |

**Quick start:**
```bash
cd backend/rust && cargo build --release
cd DRAP && cargo build --release
```

---

### 📝 Technical Writers & Documentation
**No code required — this is one of the highest-impact contributions.**

| Area | Description |
|------|-------------|
| API docs | OpenAPI/Swagger spec for all REST endpoints |
| GraphQL schema docs | Document all queries, mutations, subscriptions |
| Deployment guides | AWS, GCP, Docker, bare metal tutorials |
| SDK guides | Getting started guides for each SDK |
| Video tutorials | Setup and feature walkthroughs |
| Translations | Translate docs to other languages |

---

### 🎨 UI/UX Designers
| Area | Description |
|------|-------------|
| React Dashboard design | Figma mockups for the new React dashboard |
| DRAP Dashboard redesign | Improve the tunnel monitoring UI |
| Onboarding flow | Design the new user setup wizard |
| Component library | Build a CloudPool design system |

---

## 🚀 Quick Contribution Path (Any Stack)

1. **Pick your track** from the table above
2. **Find a `good first issue`** labeled with your stack:
   - [`good-first-issue` + `react`](https://github.com/Mr-Charvaka/CloudPool/labels/react)
   - [`good-first-issue` + `nodejs`](https://github.com/Mr-Charvaka/CloudPool/labels/nodejs)
   - [`good-first-issue` + `python`](https://github.com/Mr-Charvaka/CloudPool/labels/python)
   - [`good-first-issue` + `go`](https://github.com/Mr-Charvaka/CloudPool/labels/go)
   - [`good-first-issue` + `java`](https://github.com/Mr-Charvaka/CloudPool/labels/java)
   - [`good-first-issue` + `rust`](https://github.com/Mr-Charvaka/CloudPool/labels/rust)
   - [`good-first-issue` + `docs`](https://github.com/Mr-Charvaka/CloudPool/labels/docs)
3. **Fork, branch, code, PR** — see workflow below

---

## 🔀 Contribution Workflow

```bash
# Fork the repository on GitHub, then:
git clone https://github.com/YOUR-USERNAME/CloudPool.git
cd CloudPool

# Create a branch named after the issue
git checkout -b feat/issue-42-react-dashboard-sidebar

# Make your changes in your track's directory
# ...

# Commit with conventional commits
git commit -m "feat(react): add collapsible sidebar with project navigation"

# Push and open a PR
git push origin feat/issue-42-react-dashboard-sidebar
```

---

## 📏 Code Style

| Stack | Formatter | Command |
|-------|-----------|---------|
| Java | Google Java Style + Spotless | `mvn spotless:apply` |
| Rust | rustfmt | `cargo fmt` |
| JavaScript/TypeScript | ESLint + Prettier | `npm run lint && npm run format` |
| Python | Black + isort | `black . && isort .` |
| Go | gofmt | `go fmt ./...` |

---

## ✅ Testing Requirements

- Every new feature needs tests in its own ecosystem
- **Java**: JUnit 5, Mockito — `mvn clean test`
- **Rust**: `cargo test`
- **JS/TS**: Jest — `npm test`
- **Python**: pytest — `pytest`
- **Go**: `go test ./...`
- Coverage should not drop below **80%** in any module

---

## 💬 Getting Help

- **Discord**: [Join the CloudPool Discord](https://discord.gg/gzcnkE7yN)
- **Issues**: Open a GitHub issue with your question
- **Stack-specific channels** in Discord: `#react`, `#nodejs`, `#python`, `#go`, `#rust`, `#java`

---

## 📋 Commit Message Format

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(react):      new React feature
fix(sdk):         bug fix in JS SDK
docs(api):        API documentation update
test(python):     add Python SDK test
refactor(rust):   Rust performance improvement
chore(cli):       CLI dependency update
```

---

## 🤝 Code of Conduct

Be respectful. We welcome developers of all skill levels and backgrounds.
No gatekeeping — every stack matters in CloudPool.

---

## 📄 License

By contributing, you agree your contributions are licensed under [Apache 2.0](./LICENSE).
