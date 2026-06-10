<div align="center">
  <h1>CloudPool</h1>
  <p><strong>Developer Infrastructure Orchestration & Decentralized BaaS Platform</strong></p>
  
  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
  [![Java](https://img.shields.io/badge/Java-17%2F21-orange.svg)](https://java.com/)
  [![Rust](https://img.shields.io/badge/Rust-1.70+-black.svg)](https://www.rust-lang.org/)
  [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
  [![GraphQL](https://img.shields.io/badge/GraphQL-API-E10098.svg)](https://graphql.org/)
  [![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supported-336791.svg)](https://www.postgresql.org/)
  [![Weaviate](https://img.shields.io/badge/Weaviate-Vector_Search-blueviolet.svg)](https://weaviate.io/)
</div>

<br>

<div align="center">
  <!-- Hero Banner -->
  <img src="docs/images/hero_dashboard.png" alt="CloudPool Hero Dashboard" width="100%" style="border-radius: 8px; box-shadow: 0 4px 20px rgba(0,0,0,0.4);" />
  <p><em>Figure 1: CloudPool Developer Console Dashboard (Metrics, Storage, and Infrastructure State)</em></p>
</div>

---

## 🌩️ Overview

CloudPool is an open-source **Developer Infrastructure Orchestration & Decentralized BaaS Platform** designed to unify backend services into a single programmable infrastructure layer. 

Rather than replacing existing cloud providers, CloudPool orchestrates and abstracts infrastructure components through a centralized control plane capable of database provisioning, hybrid storage pooling, vector search indexing, state auditing, and native JNI-accelerated computations.

---

## 📸 Quick Platform Demo
If you want to view a walkthrough of the system running, check out the walkthrough GIF below:
*(For instructions on capturing your own, refer to the [Screenshot Capture Guide](docs/SCREENSHOTS_GUIDE.md))*

<div align="center">
  <img src="docs/images/quick_demo.gif" alt="CloudPool Platform Walkthrough" width="90%" style="border-radius: 6px;" />
</div>

---

## ✨ Core Features

* **Infrastructure Orchestration**: Dynamic PostgreSQL schema generation, Redis cache management, and multi-tenant database partitions.
* **Hybrid Storage Pooling**: Unifies Local Directories, AWS S3, and Google Drive behind a single zero-copy file controller.
* **Native Rust Performance Layer**: High-speed compression, hashing, and vector math operations bridged to the JVM via JNI.
* **Dynamic Provisioning Engine**: Exposes raw table creation, live updates, and live schema rollbacks via REST and GraphQL.
* **Embedded Vector Search**: Embedded Weaviate engine with native fallback capabilities for semantic and hybrid similarity search.
* **API Key Management**: Granular developer keys with token-scoped security configurations, expiration times, and live revocation.

---

## 🏛️ Platform Architecture & Flows

CloudPool separates high-level orchestration logic (JVM/Spring Boot) from performance-critical compute tasks (Rust via FFI).

### 1. System Topology

<div align="center">
  <img src="docs/images/system_architecture.png" alt="CloudPool System Topology" width="85%" style="border-radius: 6px;" />
</div>

```mermaid
flowchart TD
    A[Developer Console / Vanilla JS SPA] --> B[Spring Boot Gateway :8080]
    
    subgraph Microservices Orchestration
        B --> C[cloudpool-auth :8082]
        B --> D[cloudpool-data :8083]
        B --> E[cloudpool-compute :8084]
        B --> F[cloudpool-network :8085]
    end

    subgraph Native Compute Acceleration
        D --> G[JNI Bridge]
        G --> H[Rust Native Runtime]
        H --> H1[zstd / gzip Compression]
        H --> H2[SHA-256 Hashes]
        H --> H3[Cosine Similarity Search]
    end

    subgraph Data & Storage Layer
        D --> I[(PostgreSQL DB)]
        D --> J[(Redis Cache)]
        D --> K[(Weaviate Vector DB)]
        D --> L[Storage Pool Orchestrator]
        L --> L1[Local Directory]
        L --> L2[AWS S3 Bucket]
        L --> L3[Google Drive API]
    end
```

---

### 2. Multi-Tenant Isolation Flow
This diagram details the logical segmentation that ensures Tenant A, Tenant B, and Tenant C remain isolated across credentials, schemas, and backing storage.

```mermaid
flowchart TD
    subgraph Clients
        T1[Tenant A Client]
        T2[Tenant B Client]
        T3[Tenant C Client]
    end

    subgraph Control Plane
        Gateway[CloudPool API Gateway]
        Auth[Multi-Tenant Auth Filter]
    end

    subgraph Metadata Isolation
        DB[(PostgreSQL Database)]
        TSA[(Tenant A Schema)]
        TSB[(Tenant B Schema)]
        TSC[(Tenant C Schema)]
    end

    subgraph Storage Isolation
        Pools[Storage Orchestrator]
        SA[Bucket A - AWS S3]
        SB[Bucket B - Google Drive]
        SC[Bucket C - Local Disk]
    end

    T1 & T2 & T3 --> Gateway
    Gateway --> Auth
    
    Auth -->|Tenant A Key| TSA
    Auth -->|Tenant B Key| TSB
    Auth -->|Tenant C Key| TSC
    
    TSA --> Pools
    TSB --> Pools
    TSC --> Pools
    
    Pools --> SA
    Pools --> SB
    Pools --> SC

    classDef tenantA fill:#0d1b2a,stroke:#415a77,stroke-width:2px,color:#fff;
    classDef tenantB fill:#1b4332,stroke:#40916c,stroke-width:2px,color:#fff;
    classDef tenantC fill:#2d112c,stroke:#7209b7,stroke-width:2px,color:#fff;
    
    class T1,TSA,SA tenantA;
    class T2,TSB,SB tenantB;
    class T3,TSC,SC tenantC;
```

---

### 3. File Upload Flow Diagram
Detailed request lifecycle tracing validation, metadata cataloging, and physical file stream dispatch to the target storage adapter.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client Application
    participant Gateway as CloudPool Gateway (:8080)
    participant Auth as Auth Service (:8082)
    participant Data as Data Service (:8083)
    participant DB as PostgreSQL / Cache
    participant Provider as Storage Pool (S3 / Local / Drive)

    Client->>Gateway: POST /api/files/upload (Form File, Bucket)
    Gateway->>Auth: Validate JWT / API Key
    alt Invalid Credentials
        Auth-->>Gateway: 401 Unauthorized
        Gateway-->>Client: HTTP 401 Response
    else Valid Authorization
        Auth-->>Gateway: User Metadata & Scopes
        Gateway->>Data: Delegate File Stream
        Data->>DB: Insert FileMetadata (UUID, Size, MIME, Path)
        DB-->>Data: Record Created
        Data->>Provider: Stream Binary Payload (Zero-copy)
        Provider-->>Data: Stream Complete (Storage Identifier)
        Data-->>Gateway: FileMetadata Payload
        Gateway-->>Client: 200 OK (JSON Upload Response)
    end
```

---

## 🖼️ Developer Console screenshots

The interface consists of specialized pages for storage configuration, metrics visualization, database management, and authentication controls.

> [!TIP]
> To reproduce the application dashboard configurations and capture screenshots locally, see the detailed instructions inside [Screenshot Capture Guide](docs/SCREENSHOTS_GUIDE.md).

<table align="center" style="border-collapse: collapse; border: none; width: 100%;">
  <tr style="border: none;">
    <td style="width: 50%; border: none; padding: 10px;">
      <h4 align="center">1. File Explorer UI</h4>
      <img src="docs/images/file_explorer.png" alt="File Explorer" style="border-radius: 6px; width: 100%;" />
    </td>
    <td style="width: 50%; border: none; padding: 10px;">
      <h4 align="center">2. GraphQL Playground</h4>
      <img src="docs/images/graphql_playground.png" alt="GraphQL Playground" style="border-radius: 6px; width: 100%;" />
    </td>
  </tr>
  <tr style="border: none;">
    <td style="width: 50%; border: none; padding: 10px;">
      <h4 align="center">3. Swagger / OpenAPI</h4>
      <img src="docs/images/swagger_openapi.png" alt="Swagger UI" style="border-radius: 6px; width: 100%;" />
    </td>
    <td style="width: 50%; border: none; padding: 10px;">
      <h4 align="center">4. Storage Configuration</h4>
      <img src="docs/images/storage_providers.png" alt="Storage Providers" style="border-radius: 6px; width: 100%;" />
    </td>
  </tr>
  <tr style="border: none;">
    <td style="width: 50%; border: none; padding: 10px;">
      <h4 align="center">5. API Key Management</h4>
      <img src="docs/images/api_keys.png" alt="API Keys" style="border-radius: 6px; width: 100%;" />
    </td>
    <td style="width: 50%; border: none; padding: 10px;">
      <h4 align="center">6. Docker Compose Running</h4>
      <img src="docs/images/docker_running.png" alt="Docker Compose State" style="border-radius: 6px; width: 100%;" />
    </td>
  </tr>
  <tr style="border: none;">
    <td style="width: 50%; border: none; padding: 10px;">
      <h4 align="center">7. Monitoring Dashboard</h4>
      <img src="docs/images/monitoring_dashboard.png" alt="Grafana Monitoring" style="border-radius: 6px; width: 100%;" />
    </td>
    <td style="width: 50%; border: none; padding: 10px;">
      <h4 align="center">8. Performance Benchmark Graphs</h4>
      <img src="docs/images/benchmark_graphs.png" alt="Benchmark Performance" style="border-radius: 6px; width: 100%;" />
    </td>
  </tr>
</table>

---

## 🛠️ Technology Stack

| Architecture Layer | Technologies |
| :--- | :--- |
| **Backend Orchestration** | Java 21 (Spring Boot 3.1, Spring Cloud Gateway) |
| **Native Compute core** | Rust 1.70+ (Bridged via JNI / FFI) |
| **APIs** | GraphQL, REST (OpenAPI 3 / Swagger) |
| **Databases** | PostgreSQL 15, H2 (Local development fallback) |
| **Caching / PubSub** | Redis 7, RabbitMQ 3 |
| **Vector Engine** | Weaviate Vector Database |
| **Client Interface** | Vanilla Javascript (SPA Developer Console) |

---

## 📊 Performance Benchmarks Summary

Here is a summary of CloudPool Gateway performance under incremental concurrency. For full scenario data (database, vectors, storage comparison), read the [System Performance Benchmarks](docs/BENCHMARKS.md) page.

| Concurrent Users | Requests/sec | Avg Latency | P95 Latency | P99 Latency |
| :--- | :--- | :--- | :--- | :--- |
| **1 User** | 1,584 RPS | 0.61 ms | 1.28 ms | 2.57 ms |
| **10 Users** | 8,244 RPS | 1.17 ms | 2.45 ms | 4.90 ms |
| **100 Users** | 14,850 RPS | 6.72 ms | 14.12 ms | 28.23 ms |
| **500 Users** | 12,456 RPS | 40.11 ms | 84.23 ms | 168.47 ms |
| **1000 Users** | 8,622 RPS | 115.83 ms | 243.25 ms | 486.50 ms |

---

## 💻 API Code Examples

### 1. REST API Examples

#### Authenticate & Generate token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "developer@cloudpool.com", "password": "SecurePassword123!"}'
```

#### Upload File to Storage Pool
```bash
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer <your_jwt_token>" \
  -F "file=@/path/to/my-file.pdf" \
  -F "bucket=my-custom-pool"
```

#### Generate scoping API Key
```bash
curl -X POST http://localhost:8080/api/auth/keys \
  -H "Authorization: Bearer <your_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "production-key", "description": "Key for automated backups", "daysToLive": 30}'
```

---

### 2. GraphQL Examples

#### Query buckets and list metadata files
```graphql
query GetStorageCatalog {
  buckets {
    name
    description
  }
  files(page: 0, size: 10) {
    id
    originalName
    size
    mimeType
  }
}
```

#### Mutation to index text embeddings
```graphql
mutation IndexVectorDocument {
  indexDocument(
    collectionId: "ae45b128-4e12-4211-9a99-b1d56f78a011"
    docId: "doc_091"
    content: "Deploying cloud applications using decentralized storage backends."
    metadata: [
      { key: "category", value: "cloud-storage" },
      { key: "security", value: "public" }
    ]
  ) {
    id
    content
    status
  }
}
```

---

## 📂 Folder Structure

```text
CloudPool/
├── backend/
│   ├── spring-boot/          # Multi-module Spring Boot application
│   │   ├── cloudpool-auth/   # Identity and API Key validation
│   │   ├── cloudpool-data/   # Core File Metadata & Weaviate indexing
│   │   ├── cloudpool-compute/# Dynamic jobs & workers engine
│   │   └── cloudpool-gateway/# REST and GraphQL routing filter
│   └── rust/                 # JNI-exposed Native Rust core
├── frontend/
│   └── dashboard/            # SPA Console interface (Vanilla JS)
├── docker/                   # Docker Compose environment setups
├── kubernetes/               # Deployment manifests (deploy/service pods)
├── scripts/                  # Automation execution, deployment, & benchmarks
└── docs/                     # Architecture, metrics, & developer setup guides
```

---

## 🚀 Getting Started

### Option A — Docker Compose (Recommended)
This starts all backend microservices, DBs, and the native Rust build compilation stage automatically.

```bash
# Clone the repository
git clone https://github.com/Mr-Charvaka/CloudPool.git
cd CloudPool

# Run multi-container stack
docker compose -f docker/docker-compose.yml up --build
```
Once the containers report healthy, open the UI at `http://localhost:8080/index.html`.

### Option B — Local Manual Compilation
#### Prerequisites
* Java JDK 17 or 21
* Rust 1.70+ and Cargo
* Maven 3.9+
* Running local instances of: PostgreSQL (port 5432), Redis (6379), RabbitMQ (5672), Weaviate (8090)

#### Steps
1. **Compile Native Module**:
   ```bash
   cd backend/rust
   cargo build --release
   ```
2. **Launch Java Stack**:
   ```bash
   cd ../spring-boot
   mvn clean install
   ./launch-local.ps1
   ```

---

## ⚡ Running Automated Benchmark tests

You can verify and reproduce these performance tables locally:

```bash
# Execute local test suite
python scripts/benchmark_suite.py
```
This output can be used to populate performance charts and generate your `benchmark_graphs.png` file.

---

## 🗺️ Roadmap
* [ ] Kubernetes Dynamic Storage Class Orchestrator
* [ ] WebAssembly (WASM) plugin injection interface for dynamic runtime compute
* [ ] Multi-region bucket replication adapter
* [ ] Live Grafana dashboard templates checked in to source code

---

## 🤝 Contributing
Contributions are welcome! Please follow the workflow instructions detailed inside our [Contribution Guidelines](CONTRIBUTING.md).

---

## 📄 License
CloudPool is distributed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
