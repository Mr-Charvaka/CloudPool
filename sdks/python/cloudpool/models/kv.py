"""Data models for key-value store."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class KvEntry:
    """A key-value store entry.

    Attributes:
        key: The entry key.
        value: The entry value (any JSON-serializable type).
        ttl_seconds: Time-to-live in seconds (None = no expiry).
        created_at: Creation timestamp.
        updated_at: Last update timestamp.
    """

    key: str
    value: Any = None
    ttl_seconds: Optional[int] = None
    created_at: str = ""
    updated_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "KvEntry":
        return cls(
            key=d.get("key", ""),
            value=d.get("value"),
            ttl_seconds=d.get("ttlSeconds"),
            created_at=d.get("createdAt", ""),
            updated_at=d.get("updatedAt", ""),
        )
