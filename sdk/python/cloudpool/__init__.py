"""
CloudPool Python SDK
====================

Official Python client for the CloudPool developer infrastructure platform.

Quick start::

    from cloudpool import CloudPool

    cp = CloudPool(base_url="http://localhost:8080", api_key="cp_your_key")

    # Upload a file
    with open("report.pdf", "rb") as f:
        result = cp.storage.upload("my-bucket", f, "report.pdf")

    # Semantic search
    results = cp.vector.search_files("find invoices from 2025")

    # Run a query
    rows = cp.database.query("SELECT * FROM users LIMIT 10")
"""

from .client import CloudPool
from .storage import StorageClient
from .database import DatabaseClient
from .vector import VectorClient
from .projects import ProjectClient
from .auth import AuthClient

__version__ = "0.1.0"
__all__ = [
    "CloudPool",
    "StorageClient",
    "DatabaseClient",
    "VectorClient",
    "ProjectClient",
    "AuthClient",
]
