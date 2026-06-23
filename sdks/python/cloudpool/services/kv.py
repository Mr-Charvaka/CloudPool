"""Key-value store service."""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from cloudpool._client import CloudPoolClient
from cloudpool.models.kv import KvEntry


class KvClient:
    """Synchronous key-value store client.

    Distributed key-value storage with optional TTL.

    Accessed via ``cloudpool.kv`` on a ``CloudPool`` instance.
    """

    def __init__(self, client: CloudPoolClient) -> None:
        self._client = client

    def set(
        self,
        project_id: str,
        key: str,
        value: Any,
        ttl_seconds: Optional[int] = None,
    ) -> KvEntry:
        """Set a key-value pair.

        Args:
            project_id: The project's unique identifier.
            key: The key name.
            value: The value (any JSON-serializable type).
            ttl_seconds: Optional time-to-live in seconds.

        Returns:
            The created KvEntry.
        """
        body: Dict[str, Any] = {"value": value}
        if ttl_seconds is not None:
            body["ttlSeconds"] = ttl_seconds
        resp = self._client.request(
            "PUT", f"/api/v1/projects/{project_id}/kv/{key}", json=body,
        )
        return KvEntry.from_dict(resp)

    def get(self, project_id: str, key: str) -> KvEntry:
        """Get a value by key.

        Args:
            project_id: The project's unique identifier.
            key: The key name.

        Returns:
            The KvEntry if found.
        """
        resp = self._client.request(
            "GET", f"/api/v1/projects/{project_id}/kv/{key}",
        )
        return KvEntry.from_dict(resp)

    def list(self, project_id: str) -> List[KvEntry]:
        """List all key-value entries for a project.

        Args:
            project_id: The project's unique identifier.

        Returns:
            List of KvEntry objects.
        """
        resp = self._client.request("GET", f"/api/v1/projects/{project_id}/kv")
        return [KvEntry.from_dict(e) for e in (resp if isinstance(resp, list) else [])]

    def delete(self, project_id: str, key: str) -> None:
        """Delete a key-value entry.

        Args:
            project_id: The project's unique identifier.
            key: The key name.
        """
        self._client.request("DELETE", f"/api/v1/projects/{project_id}/kv/{key}")


class AsyncKvClient:
    """Asynchronous key-value store client."""

    def __init__(self, client: Any) -> None:
        self._client = client

    async def set(self, project_id: str, key: str, value: Any, ttl_seconds: Optional[int] = None) -> KvEntry:
        body: Dict[str, Any] = {"value": value}
        if ttl_seconds is not None:
            body["ttlSeconds"] = ttl_seconds
        resp = await self._client.request("PUT", f"/api/v1/projects/{project_id}/kv/{key}", json=body)
        return KvEntry.from_dict(resp)

    async def get(self, project_id: str, key: str) -> KvEntry:
        resp = await self._client.request("GET", f"/api/v1/projects/{project_id}/kv/{key}")
        return KvEntry.from_dict(resp)

    async def list(self, project_id: str) -> List[KvEntry]:
        resp = await self._client.request("GET", f"/api/v1/projects/{project_id}/kv")
        return [KvEntry.from_dict(e) for e in (resp if isinstance(resp, list) else [])]

    async def delete(self, project_id: str, key: str) -> None:
        await self._client.request("DELETE", f"/api/v1/projects/{project_id}/kv/{key}")
