"""Data models for email service."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class Email:
    """An email message record.

    Attributes:
        id: Unique email identifier.
        to_addr: Recipient email address.
        subject: Email subject line.
        body: Email body content.
        status: Delivery status (e.g., "sent", "failed").
        created_at: Send timestamp.
    """

    id: str
    to_addr: str = ""
    subject: str = ""
    body: str = ""
    status: str = ""
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Email":
        return cls(
            id=d.get("id", ""),
            to_addr=d.get("to", d.get("toAddr", "")),
            subject=d.get("subject", ""),
            body=d.get("body", ""),
            status=d.get("status", ""),
            created_at=d.get("createdAt", ""),
        )
