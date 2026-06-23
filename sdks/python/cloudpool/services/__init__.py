"""Service clients for CloudPool API modules."""

from __future__ import annotations

from cloudpool.services.auth import AuthClient, AsyncAuthClient
from cloudpool.services.files import FilesClient, AsyncFilesClient
from cloudpool.services.database import DatabaseClient, AsyncDatabaseClient
from cloudpool.services.vector import VectorClient, AsyncVectorClient
from cloudpool.services.compute import ComputeClient, AsyncComputeClient
from cloudpool.services.network import NetworkClient, AsyncNetworkClient
from cloudpool.services.payments import PaymentsClient, AsyncPaymentsClient
from cloudpool.services.kv import KvClient, AsyncKvClient
from cloudpool.services.emails import EmailsClient, AsyncEmailsClient

__all__ = [
    "AuthClient",
    "AsyncAuthClient",
    "FilesClient",
    "AsyncFilesClient",
    "DatabaseClient",
    "AsyncDatabaseClient",
    "VectorClient",
    "AsyncVectorClient",
    "ComputeClient",
    "AsyncComputeClient",
    "NetworkClient",
    "AsyncNetworkClient",
    "PaymentsClient",
    "AsyncPaymentsClient",
    "KvClient",
    "AsyncKvClient",
    "EmailsClient",
    "AsyncEmailsClient",
]
