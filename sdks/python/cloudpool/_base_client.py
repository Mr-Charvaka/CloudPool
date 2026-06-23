"""Shared base for sync and async HTTP clients."""

from __future__ import annotations

from typing import Any, Dict, Optional

from cloudpool._config import CloudPoolConfig
from cloudpool._credential import CredentialChain
from cloudpool._retry import exponential_backoff


class BaseCloudPoolClient:
    """Base class with shared logic for sync and async clients.

    Attributes:
        config: The SDK configuration.
        credential_provider: The credential chain for authentication.
        jwt_token: Current JWT token (may be updated by login).
        api_key: Current API key (may be updated by constructor).
    """

    def __init__(
        self,
        config: CloudPoolConfig,
        credential_provider: Optional[CredentialChain] = None,
    ) -> None:
        self.config: CloudPoolConfig = config
        self._credential_provider: CredentialChain = (
            credential_provider or CredentialChain()
        )
        self.jwt_token: Optional[str] = config.jwt_token
        self.api_key: Optional[str] = config.api_key

    def _resolve_auth_headers(self) -> Dict[str, str]:
        """Build auth headers from current credentials.

        Priority: API key > JWT token > credential chain > None.
        """
        headers: Dict[str, str] = {}
        if self.api_key:
            headers["X-API-KEY"] = self.api_key
        elif self.jwt_token:
            headers["Authorization"] = f"Bearer {self.jwt_token}"
        else:
            resolved = self._credential_provider.resolve()
            if resolved.get("api_key"):
                headers["X-API-KEY"] = resolved["api_key"]
                self.api_key = resolved["api_key"]
            elif resolved.get("jwt_token"):
                headers["Authorization"] = f"Bearer {resolved['jwt_token']}"
                self.jwt_token = resolved["jwt_token"]
        return headers

    def _build_headers(
        self,
        extra: Optional[Dict[str, str]] = None,
    ) -> Dict[str, str]:
        """Build complete request headers.

        Args:
            extra: Additional headers to merge.

        Returns:
            Header dict with auth, user-agent, content-type, etc.
        """
        from cloudpool._utils import build_user_agent

        headers = self._resolve_auth_headers()
        headers.setdefault("User-Agent", build_user_agent())
        headers.setdefault("Accept", "application/json")
        if extra:
            headers.update(extra)
        return headers

    def set_jwt_token(self, token: str) -> None:
        """Update the JWT token at runtime.

        Args:
            token: New JWT token string.
        """
        self.jwt_token = token
        self.api_key = None

    def set_api_key(self, key: str) -> None:
        """Update the API key at runtime.

        Args:
            key: New API key string.
        """
        self.api_key = key
        self.jwt_token = None

    def _retry_delay(self, attempt: int) -> float:
        """Calculate retry delay using exponential backoff with jitter.

        Args:
            attempt: Zero-indexed attempt number.

        Returns:
            Delay in seconds.
        """
        return exponential_backoff(
            attempt=attempt,
            base_delay=0.5,
            max_delay=30.0,
            jitter=True,
        )
