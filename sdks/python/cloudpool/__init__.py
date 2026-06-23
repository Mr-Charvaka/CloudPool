"""
CloudPool Python SDK — AI-Native Backend Platform.

Provides both synchronous and asynchronous clients with full type safety,
automatic retry with exponential backoff, credential chain resolution,
and comprehensive API coverage.

Typical usage:

    from cloudpool import CloudPool

    # Sync
    cp = CloudPool(api_key="sk-...")
    me = cp.auth.login("user@example.com", "password")
    files = cp.files.list()

    # Async
    import asyncio
    async def main():
        async with CloudPool.async_client(api_key="sk-...") as cp:
            me = await cp.auth.login("user@example.com", "password")
            files = await cp.files.list()
"""

from __future__ import annotations

import os
from typing import Any, Dict, Optional

from cloudpool._version import __version__, __version_info__
from cloudpool.exceptions import (
    CloudPoolError,
    AuthenticationError,
    RateLimitError,
    NotFoundError,
    ValidationError,
    ConflictError,
    GraphQLError,
    ConfigurationError,
)
from cloudpool._config import CloudPoolConfig, ConfigLoader
from cloudpool._credential import CredentialChain, EnvCredentialProvider, FileCredentialProvider

__all__ = [
    # Main client
    "CloudPool",
    # Sub-clients (accessible as attributes)
    "AuthClient",
    "FilesClient",
    "DatabaseClient",
    "VectorClient",
    "ComputeClient",
    "NetworkClient",
    "PaymentsClient",
    "KvClient",
    "EmailsClient",
    # Exceptions
    "CloudPoolError",
    "AuthenticationError",
    "RateLimitError",
    "NotFoundError",
    "ValidationError",
    "ConflictError",
    "GraphQLError",
    "ConfigurationError",
    # Utilities
    "CloudPoolConfig",
    "__version__",
    "__version_info__",
]

class CloudPool:
    """Primary entry point for the CloudPool SDK.

    Provides attribute-based access to all service clients:

        cp = CloudPool(jwt_token="...")
        cp.files.upload("photo.jpg")
        cp.database.query("SELECT * FROM users")

    Args:
        base_url: Base URL of the CloudPool API.
        api_key: API key for authentication (mutually exclusive with jwt_token).
        jwt_token: JWT token for authentication (mutually exclusive with api_key).
        timeout: Default request timeout in seconds.
        max_retries: Maximum number of retry attempts for failed requests.
        config: Pre-built CloudPoolConfig object (overrides other constructor args).
        verbose: Enable verbose logging.

    Raises:
        ConfigurationError: If both api_key and jwt_token are provided, or
            if no authentication method is available.
    """

    def __init__(
        self,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        jwt_token: Optional[str] = None,
        timeout: int = 30,
        max_retries: int = 3,
        config: Optional[CloudPoolConfig] = None,
        verbose: bool = False,
    ) -> None:
        if config is not None:
            cfg = config
        else:
            cfg = ConfigLoader().load(
                base_url=base_url,
                api_key=api_key,
                jwt_token=jwt_token,
                timeout=timeout,
                max_retries=max_retries,
                verbose=verbose,
            )
        self._config: CloudPoolConfig = cfg
        self._credential_provider: CredentialChain = CredentialChain()

        from cloudpool._client import CloudPoolClient
        self._client = CloudPoolClient(cfg, self._credential_provider)

        self._init_services()

    def _init_services(self) -> None:
        from cloudpool.services.auth import AuthClient
        from cloudpool.services.files import FilesClient
        from cloudpool.services.database import DatabaseClient
        from cloudpool.services.vector import VectorClient
        from cloudpool.services.compute import ComputeClient
        from cloudpool.services.network import NetworkClient
        from cloudpool.services.payments import PaymentsClient
        from cloudpool.services.kv import KvClient
        from cloudpool.services.emails import EmailsClient

        self.auth: AuthClient = AuthClient(self._client)
        self.files: FilesClient = FilesClient(self._client)
        self.database: DatabaseClient = DatabaseClient(self._client)
        self.vector: VectorClient = VectorClient(self._client)
        self.compute: ComputeClient = ComputeClient(self._client)
        self.network: NetworkClient = NetworkClient(self._client)
        self.payments: PaymentsClient = PaymentsClient(self._client)
        self.kv: KvClient = KvClient(self._client)
        self.emails: EmailsClient = EmailsClient(self._client)

    @classmethod
    def async_client(
        cls,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        jwt_token: Optional[str] = None,
        timeout: int = 30,
        max_retries: int = 3,
        config: Optional[CloudPoolConfig] = None,
        verbose: bool = False,
    ) -> "AsyncCloudPool":
        """Create an async CloudPool client.

        Returns an AsyncCloudPool instance that wraps all service clients
        with async/await API.

        Example:
            async with CloudPool.async_client(api_key="...") as cp:
                result = await cp.files.list()
        """
        from cloudpool._async_client import AsyncCloudPool
        return AsyncCloudPool(
            base_url=base_url,
            api_key=api_key,
            jwt_token=jwt_token,
            timeout=timeout,
            max_retries=max_retries,
            config=config,
            verbose=verbose,
        )

    def health(self, details: bool = False) -> Dict[str, Any]:
        """Check API health.

        Args:
            details: If True, returns per-service status breakdown.

        Returns:
            Health status dict with keys like 'status', 'gateway', 'data',
            'auth', 'compute', 'network', 'weaviate'.
        """
        return self._client.request(
            "GET", "/api/health",
            params={"details": "true"} if details else {},
        )

    def set_jwt_token(self, token: str) -> None:
        """Update the JWT token at runtime.

        Args:
            token: New JWT token string.
        """
        self._client.set_jwt_token(token)

    def set_api_key(self, key: str) -> None:
        """Update the API key at runtime.

        Args:
            key: New API key string.
        """
        self._client.set_api_key(key)

    @property
    def base_url(self) -> str:
        return self._config.base_url

    def close(self) -> None:
        """Close the underlying HTTP session and free resources."""
        self._client.close()

    def __enter__(self) -> CloudPool:
        return self

    def __exit__(self, *exc: Any) -> None:
        self.close()
