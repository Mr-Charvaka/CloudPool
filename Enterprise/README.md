# CloudPool for Enterprise

**Self-hosted BaaS platform with full production infrastructure.**

## What you get

| Feature | What it does |
|---|---|
| **All Student features** | Auth, file storage, DB API, vector search, email |
| **Storage Pooling** | S3 + Google Drive + Local disk behind one API |
| **Payment Processing** | Stripe & Razorpay integration |
| **Serverless Functions** | GraalVM sandboxed JS execution |
| **WAF & Rate Limiting** | Web application firewall per tenant |
| **Cloud Tunnels** | Enterprise tunnel management |
| **Monitoring** | Prometheus, Grafana, OpenTelemetry tracing |
| **Kubernetes** | Production deployment manifests |
| **Rust JNI** | Native performance for compression, hashing, vectors |

## Quick start

```bash
# Start full infrastructure
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml up -d

# Build (enterprise profile — full stack)
cd backend/spring-boot
mvn clean install -P enterprise

# Deploy to Kubernetes
kubectl apply -f kubernetes/
```

## Architecture

6 Spring Boot microservices + Rust JNI acceleration layer + 4 language SDKs.
Full OpenTelemetry tracing, Prometheus metrics, and structured logging.
