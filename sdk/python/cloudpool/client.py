"""
CloudPool Python Client
"""
from __future__ import annotations
from typing import Any, Optional
import requests


class CloudPool:
    """
    Main entry point for the CloudPool Python SDK.

    Args:
        base_url: URL of your CloudPool instance (e.g. http://localhost:8080)
        api_key: API key for authentication (preferred for server-side scripts)
        token: JWT token for authentication

    Example::

        cp = CloudPool(base_url="http://localhost:8080", api_key="cp_abc123")
        projects = cp.projects.list()
    """

    def __init__(
        self,
        base_url: str,
        api_key: Optional[str] = None,
        token: Optional[str] = None,
    ):
        from .storage import StorageClient
        from .database import DatabaseClient
        from .vector import VectorClient
        from .projects import ProjectClient
        from .auth import AuthClient

        self.base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._token = token
        self._session = requests.Session()

        self.storage = StorageClient(self)
        self.database = DatabaseClient(self)
        self.vector = VectorClient(self)
        self.projects = ProjectClient(self)
        self.auth = AuthClient(self)

    def _headers(self) -> dict[str, str]:
        headers: dict[str, str] = {"Content-Type": "application/json"}
        if self._api_key:
            headers["X-API-Key"] = self._api_key
        elif self._token:
            headers["Authorization"] = f"Bearer {self._token}"
        return headers

    def request(
        self,
        method: str,
        path: str,
        json: Any = None,
        data: Any = None,
        files: Any = None,
    ) -> Any:
        """Make an authenticated request to the CloudPool REST API."""
        url = f"{self.base_url}{path}"
        headers = self._headers()

        if files:
            # Don't set Content-Type for multipart
            headers.pop("Content-Type", None)

        resp = self._session.request(
            method=method,
            url=url,
            headers=headers,
            json=json,
            data=data,
            files=files,
        )

        try:
            resp.raise_for_status()
        except requests.HTTPError as e:
            raise CloudPoolError(f"API error ({resp.status_code}): {resp.text}") from e

        if resp.content:
            return resp.json()
        return None

    def set_token(self, token: str) -> None:
        """Update the JWT token after login."""
        self._token = token


class CloudPoolError(Exception):
    """Raised when the CloudPool API returns an error."""
    pass
