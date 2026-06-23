# CloudPool

**Two distributions. One codebase.**

```
CloudPool/
├── Student-Researcher/     # For students: deploy with tunnels + built-in backend
│   ├── DRAP/               # Tunnel platform (expose localhost to internet)
│   ├── docker/             # Simplified stack (postgres, redis, weaviate)
│   └── README.md
├── Enterprise/             # For teams: full BaaS + production infrastructure
│   ├── kubernetes/         # K8s deployment manifests
│   ├── docker/             # Full stack with monitoring
│   ├── tests/              # Load & integration tests
│   └── README.md
├── backend/
│   ├── spring-boot/        # 6 Java microservices (shared source)
│   └── rust/               # JNI acceleration layer
├── frontend/dashboard/     # SPA (shared)
├── cli/                    # CLI tool (shared)
└── sdks/                   # 4 language SDKs (shared)
```

## Build

```bash
# Student build (lighter, tunnel-focused)
cd Student-Researcher
.\build.ps1

# Enterprise build (full stack, K8s-ready)
cd Enterprise
.\build.ps1
```

## Why two?

- **Student-Researcher** — you want to ship projects fast. `cloudpool deploy` gives you a URL + auth/db/storage APIs. No cloud bill.
- **Enterprise** — you need S3/Drive storage pooling, payment processing, serverless compute, monitoring, K8s deployment, and the Rust JNI performance layer.
