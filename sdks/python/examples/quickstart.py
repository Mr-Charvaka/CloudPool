"""CloudPool Python SDK — Quickstart Example.

Run with:
    pip install cloudpool-sdk
    python examples/quickstart.py
"""

from __future__ import annotations


def sync_example():
    """Demonstrates the synchronous CloudPool client."""
    from cloudpool import CloudPool

    # Initialize with authentication
    cp = CloudPool(
        base_url="https://api.cloudpool.dev",
        jwt_token="your-jwt-token-here",  # or use api_key="sk-..."
    )

    # --- Auth ---
    user = cp.auth.me()
    print(f"Logged in as: {user.name} ({user.email})")

    # --- Files ---
    try:
        metadata = cp.files.upload("example.txt", bucket="my-bucket")
        print(f"Uploaded: {metadata.original_name} ({metadata.size} bytes)")

        files = cp.files.list()
        print(f"Total files: {len(files)}")

        quota = cp.files.get_quota()
        print(f"Storage: {quota['usage']} / {quota['limit']} bytes")
    except FileNotFoundError:
        print("Upload example requires 'example.txt' to exist.")

    # --- Database ---
    tables = cp.database.list_tables()
    print(f"Database tables: {len(tables)}")
    for t in tables:
        print(f"  - {t.name} ({t.display_name})")

    # --- Vector ---
    results = cp.vector.search("documents", "how do I deploy?")
    print(f"Vector search results: {len(results)}")

    # Raise typed exceptions
    try:
        cp.files.download("nonexistent-id")
    except Exception as e:
        print(f"Expected error: {type(e).__name__}: {e}")

    # Cleanup
    cp.close()


async def async_example():
    """Demonstrates the async CloudPool client."""
    from cloudpool import CloudPool

    async with CloudPool.async_client(
        base_url="https://api.cloudpool.dev",
        jwt_token="your-jwt-token-here",
    ) as cp:
        user = await cp.auth.me()
        print(f"Async - Logged in as: {user.name}")

        files = await cp.files.list()
        print(f"Async - Files: {len(files)}")


if __name__ == "__main__":
    print("=" * 60)
    print("CloudPool Python SDK — Quickstart")
    print("=" * 60)

    sync_example()

    import asyncio
    asyncio.run(async_example())
