"""Data models for vector search."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class VectorCollection:
    """A vector search index/collection.

    Attributes:
        id: Unique collection identifier.
        name: Collection name.
        description: Optional description.
        dimension: Vector dimensionality.
        distance_metric: Distance metric (e.g., "cosine", "euclidean", "dot").
        created_at: Creation timestamp.
    """

    id: str
    name: str
    description: str = ""
    dimension: int = 1536
    distance_metric: str = "cosine"
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "VectorCollection":
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            description=d.get("description", ""),
            dimension=d.get("dimension", 1536),
            distance_metric=d.get("distanceMetric", "cosine"),
            created_at=d.get("createdAt", ""),
        )
