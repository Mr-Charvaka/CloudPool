"""Vector search service."""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from cloudpool._client import CloudPoolClient
from cloudpool.models.vector import VectorCollection


class VectorClient:
    """Synchronous vector search client.

    Search indexes, import vectors, manage collections.

    Accessed via ``cloudpool.vector`` on a ``CloudPool`` instance.
    """

    def __init__(self, client: CloudPoolClient) -> None:
        self._client = client

    def search(
        self,
        index: str,
        query: str,
        limit: int = 10,
        filters: Optional[Dict[str, Any]] = None,
    ) -> List[Dict[str, Any]]:
        """Search a vector index.

        Args:
            index: The vector index/collection name.
            query: Natural language query string.
            limit: Maximum number of results.
            filters: Optional metadata filters.

        Returns:
            List of result dicts with 'id', 'score', 'text', and metadata.
        """
        body: Dict[str, Any] = {"query": query, "limit": limit}
        if filters:
            body["filters"] = filters
        resp = self._client.request(
            "POST", f"/api/vector/{index}/search", json=body,
        )
        return resp if isinstance(resp, list) else []

    def import_vectors(
        self,
        index: str,
        vectors: List[Dict[str, Any]],
    ) -> int:
        """Import vectors into an index.

        Args:
            index: The vector index/collection name.
            vectors: List of vector objects with 'id', 'vector', and
                optional 'metadata' fields.

        Returns:
            Number of vectors imported.
        """
        resp = self._client.request(
            "POST", f"/api/vector/{index}/import", json=vectors,
        )
        if isinstance(resp, dict):
            return resp.get("imported", 0)
        return 0

    def delete(self, index: str, vector_id: str) -> None:
        """Delete a vector by ID.

        Args:
            index: The vector index/collection name.
            vector_id: The vector's unique identifier.
        """
        self._client.request("DELETE", f"/api/vector/{index}/{vector_id}")

    def get_schema(self, index: str) -> Optional[VectorCollection]:
        """Get the schema for a vector index.

        Args:
            index: The vector index/collection name.

        Returns:
            VectorCollection with schema details, or None.
        """
        resp = self._client.request("GET", f"/api/vector/{index}/schema")
        if isinstance(resp, dict):
            return VectorCollection.from_dict(resp)
        return None

    def list_collections(self) -> List[VectorCollection]:
        """List all vector collections.

        Returns:
            List of VectorCollection objects.
        """
        resp = self._client.request("GET", "/api/vector")
        return [VectorCollection.from_dict(c) for c in (resp if isinstance(resp, list) else [])]


class AsyncVectorClient:
    """Asynchronous vector search client."""

    def __init__(self, client: Any) -> None:
        self._client = client

    async def search(self, index: str, query: str, limit: int = 10) -> List[Dict[str, Any]]:
        resp = await self._client.request("POST", f"/api/vector/{index}/search", json={"query": query, "limit": limit})
        return resp if isinstance(resp, list) else []

    async def import_vectors(self, index: str, vectors: List[Dict[str, Any]]) -> int:
        resp = await self._client.request("POST", f"/api/vector/{index}/import", json=vectors)
        if isinstance(resp, dict):
            return resp.get("imported", 0)
        return 0

    async def delete(self, index: str, vector_id: str) -> None:
        await self._client.request("DELETE", f"/api/vector/{index}/{vector_id}")

    async def get_schema(self, index: str) -> Optional[VectorCollection]:
        resp = await self._client.request("GET", f"/api/vector/{index}/schema")
        if isinstance(resp, dict):
            return VectorCollection.from_dict(resp)
        return None

    async def list_collections(self) -> List[VectorCollection]:
        resp = await self._client.request("GET", "/api/vector")
        return [VectorCollection.from_dict(c) for c in (resp if isinstance(resp, list) else [])]
