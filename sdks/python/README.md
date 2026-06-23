# CloudPool Python SDK

Official Python client for the [CloudPool](https://cloudpool.dev) BaaS platform.

## Installation

```bash
pip install cloudpool-sdk
```

Or from source:

```bash
cd sdks/python
pip install .
```

## Quick Start

```python
from cloudpool import CloudPool

# Connect with JWT token (after login)
cp = CloudPool(base_url="http://localhost:8080")
tokens = cp.auth.login("user@example.com", "your-password")

# Or use an API key
cp = CloudPool(base_url="http://localhost:8080", api_key="cp_live_...")

# Upload a file
file = cp.files.upload("report.pdf", bucket="my-bucket")

# List files
files = cp.files.list()

# Create a database table
table = cp.database.create_table(
    name="users",
    display_name="Users",
    description="User profiles",
    fields=[
        {"fieldName": "name", "fieldType": "text", "required": True},
        {"fieldName": "email", "fieldType": "text", "required": True},
    ],
)

# Insert a record
cp.database.insert_record(table.id, {"name": "Alice", "email": "alice@example.com"})

# Vector search on uploaded files
results = cp.vector.search_files("machine learning")

# Deploy a container
cp.compute.deploy_container(
    name="my-app",
    docker_image="nginx:latest",
    cpu="0.5",
    memory="512Mi",
)

# Start a tunnel
tunnel = cp.network.start_tunnel(port=3000, subdomain="my-app")

# Create a charge (Stripe/Razorpay)
charge = cp.payments.create_charge(
    gateway_id="gw-xxx",
    amount=1999,
    currency="USD",
    description="Pro plan",
)
```

## Modules

| Module | Client | Description |
|--------|--------|-------------|
| Auth | `cp.auth` | Login, register, JWT refresh, API keys, projects, secrets, snapshots |
| Files | `cp.files` | Upload, download, share, buckets, quota, audit logs |
| Database | `cp.database` | Create/manage tables, insert/query records (REST + GraphQL) |
| Vector | `cp.vector` | Semantic file search, vector collections, document indexing |
| Compute | `cp.compute` | Static sites, serverless functions, containers, cron jobs |
| Network | `cp.network` | Tunnels, WAF rules, Pub/Sub |
| Payments | `cp.payments` | Payment gateway registration, charges, transactions |
| KV Store | `cp.kv` | Key-value store with TTL |
| Emails | `cp.emails` | Send/receive emails, test SMTP |

## Error Handling

```python
from cloudpool import CloudPool, CloudPoolError, AuthenticationError, NotFoundError

cp = CloudPool(api_key="cp_live_...")

try:
    files = cp.files.list()
except AuthenticationError:
    print("Bad credentials")
except NotFoundError:
    print("Resource not found")
except CloudPoolError as e:
    print(f"API error {e.status_code}: {e}")
```
