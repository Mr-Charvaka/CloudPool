"""Data models for networking services."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class TunnelStatus:
    """Status of a network tunnel.

    Attributes:
        subdomain: The tunnel's subdomain.
        port: Local port being tunneled.
        status: Tunnel status (e.g., "active", "stopped").
        public_url: Public URL for the tunnel.
        started_at: When the tunnel was started.
    """

    subdomain: str
    port: int = 0
    status: str = ""
    public_url: str = ""
    started_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "TunnelStatus":
        return cls(
            subdomain=d.get("subdomain", ""),
            port=d.get("port", 0),
            status=d.get("status", ""),
            public_url=d.get("publicUrl", ""),
            started_at=d.get("startedAt", ""),
        )


@dataclass
class WafRule:
    """A Web Application Firewall rule.

    Attributes:
        id: Unique rule identifier.
        rule_type: Rule type (e.g., "ip_blacklist", "rate_limit").
        pattern: The matching pattern.
        action: Action on match (e.g., "block", "allow", "challenge").
        created_at: Creation timestamp.
    """

    id: str
    rule_type: str = ""
    pattern: str = ""
    action: str = ""
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "WafRule":
        return cls(
            id=d["id"],
            rule_type=d.get("ruleType", ""),
            pattern=d.get("pattern", ""),
            action=d.get("action", ""),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class PubSubMessage:
    """A published PubSub message.

    Attributes:
        channel: The channel the message was published to.
        payload: The message payload.
        published_at: Publication timestamp.
    """

    channel: str
    payload: Any = None
    published_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "PubSubMessage":
        return cls(
            channel=d.get("channel", ""),
            payload=d.get("payload", d.get("payloadJson")),
            published_at=d.get("publishedAt", ""),
        )
