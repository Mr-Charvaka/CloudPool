"""GraphQL response models."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class GraphQLResponse:
    """Typed wrapper for a standard GraphQL JSON envelope.

    The ``data`` field is a ``dict`` whose shape depends on the query.
    Callers should cast the sub-fields as needed (e.g.
    ``response.data.get("users", [])``).
    """

    data: Dict[str, Any] = field(default_factory=dict)
    errors: Optional[List[Dict[str, Any]]] = None

    @classmethod
    def from_raw(cls, raw: Dict[str, Any]) -> GraphQLResponse:
        """Create from a raw GraphQL JSON dict."""
        return cls(
            data=raw.get("data", {}),
            errors=raw.get("errors"),
        )
