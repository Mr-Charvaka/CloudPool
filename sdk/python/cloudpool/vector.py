from __future__ import annotations

from typing import Any


class VectorClient:
    """Vector search APIs."""

    def __init__(self, cp):
        self.cp = cp

    def list_collections(self) -> list[dict[str, Any]]:
        return self.cp.request(
            "GET",
            "/api/vector/collections",
        )

    def create_collection(
        self,
        name: str,
        description: str,
        dimension: int = 1536,
        distance_metric: str = "cosine",
    ) -> dict[str, Any]:
        return self.cp.request(
            "POST",
            "/api/vector/collections",
            json={
                "name": name,
                "description": description,
                "dimension": dimension,
                "distanceMetric": distance_metric,
            },
        )

    def delete_collection(
        self,
        collection_id: str,
    ) -> None:
        self.cp.request(
            "DELETE",
            f"/api/vector/collections/{collection_id}",
        )

    def index_document(
        self,
        collection_id: str,
        doc_id: str,
        content: str,
        metadata: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        return self.cp.request(
            "POST",
            f"/api/vector/collections/{collection_id}/documents",
            json={
                "docId": doc_id,
                "content": content,
                "metadata": metadata,
            },
        )

    def search_collection(
        self,
        collection_id: str,
        query: str,
        limit: int = 10,
    ) -> list[dict[str, Any]]:
        return self.cp.request(
            "GET",
            f"/api/vector/collections/{collection_id}/search?query={query}&limit={limit}",
        )

    def search_files(
        self,
        query: str,
    ) -> list[dict[str, Any]]:
        return self.cp.request(
            "GET",
            f"/api/vector/search?query={query}",
        )