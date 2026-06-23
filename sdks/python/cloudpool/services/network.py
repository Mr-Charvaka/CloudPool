"""Network service (tunnels, WAF, pubsub)."""

from __future__ import annotations

from typing import Any, Dict, List

from cloudpool._client import CloudPoolClient
from cloudpool.models.network import PubSubMessage, TunnelStatus, WafRule


class NetworkClient:
    """Synchronous network client.

    Manages tunnels, Web Application Firewall rules, and pub/sub
    messaging.

    Accessed via ``cloudpool.network`` on a ``CloudPool`` instance.
    """

    def __init__(self, client: CloudPoolClient) -> None:
        self._client = client

    # ── Tunnels ──

    def start_tunnel(self, port: int, subdomain: str = "") -> TunnelStatus:
        """Start a network tunnel.

        Args:
            port: Local port to tunnel.
            subdomain: Requested subdomain (optional).

        Returns:
            TunnelStatus with the public URL.
        """
        resp = self._client.request(
            "POST", "/api/v1/tunnels/start",
            json={"port": port, "subdomain": subdomain},
        )
        return TunnelStatus.from_dict(resp)

    def stop_tunnel(self, subdomain: str) -> None:
        """Stop a running tunnel.

        Args:
            subdomain: The tunnel's subdomain.
        """
        self._client.request("POST", f"/api/v1/tunnels/stop/{subdomain}")

    def get_tunnel_status(self, subdomain: str) -> TunnelStatus:
        """Get the status of a tunnel.

        Args:
            subdomain: The tunnel's subdomain.

        Returns:
            TunnelStatus with current state.
        """
        resp = self._client.request("GET", f"/api/v1/tunnels/status/{subdomain}")
        return TunnelStatus.from_dict(resp)

    # ── WAF ──

    def list_waf_rules(self, project_id: str) -> List[WafRule]:
        """List WAF rules for a project.

        Args:
            project_id: The project's unique identifier.

        Returns:
            List of WafRule objects.
        """
        resp = self._client.request("GET", f"/api/v1/projects/{project_id}/waf")
        return [WafRule.from_dict(r) for r in (resp if isinstance(resp, list) else [])]

    def add_waf_rule(self, project_id: str, rule_type: str, pattern: str, action: str) -> WafRule:
        """Add a WAF rule.

        Args:
            project_id: The project's unique identifier.
            rule_type: Rule type (e.g., "ip_blacklist", "rate_limit").
            pattern: Matching pattern.
            action: Action on match ("block", "allow", "challenge").

        Returns:
            The created WafRule.
        """
        resp = self._client.request(
            "POST", f"/api/v1/projects/{project_id}/waf",
            json={"ruleType": rule_type, "pattern": pattern, "action": action},
        )
        return WafRule.from_dict(resp)

    def delete_waf_rule(self, project_id: str, rule_id: str) -> None:
        """Delete a WAF rule.

        Args:
            project_id: The project's unique identifier.
            rule_id: The rule's unique identifier.
        """
        self._client.request("DELETE", f"/api/v1/projects/{project_id}/waf/{rule_id}")

    # ── Pub/Sub ──

    def broadcast(self, project_id: str, channel: str, payload: Any) -> PubSubMessage:
        """Broadcast a message to a PubSub channel.

        Args:
            project_id: The project's unique identifier.
            channel: Channel name.
            payload: Message payload (JSON-serializable).

        Returns:
            PubSubMessage with publication details.
        """
        resp = self._client.request(
            "POST", f"/api/v1/projects/{project_id}/pubsub/broadcast",
            json={"channel": channel, "payloadJson": payload},
        )
        return PubSubMessage.from_dict(resp)


class AsyncNetworkClient:
    """Asynchronous network client."""

    def __init__(self, client: Any) -> None:
        self._client = client

    async def start_tunnel(self, port: int, subdomain: str = "") -> TunnelStatus:
        resp = await self._client.request("POST", "/api/v1/tunnels/start", json={"port": port, "subdomain": subdomain})
        return TunnelStatus.from_dict(resp)

    async def stop_tunnel(self, subdomain: str) -> None:
        await self._client.request("POST", f"/api/v1/tunnels/stop/{subdomain}")

    async def get_tunnel_status(self, subdomain: str) -> TunnelStatus:
        resp = await self._client.request("GET", f"/api/v1/tunnels/status/{subdomain}")
        return TunnelStatus.from_dict(resp)

    async def list_waf_rules(self, project_id: str) -> List[WafRule]:
        resp = await self._client.request("GET", f"/api/v1/projects/{project_id}/waf")
        return [WafRule.from_dict(r) for r in (resp if isinstance(resp, list) else [])]

    async def add_waf_rule(self, project_id: str, rule_type: str, pattern: str, action: str) -> WafRule:
        resp = await self._client.request("POST", f"/api/v1/projects/{project_id}/waf", json={"ruleType": rule_type, "pattern": pattern, "action": action})
        return WafRule.from_dict(resp)

    async def delete_waf_rule(self, project_id: str, rule_id: str) -> None:
        await self._client.request("DELETE", f"/api/v1/projects/{project_id}/waf/{rule_id}")

    async def broadcast(self, project_id: str, channel: str, payload: Any) -> PubSubMessage:
        resp = await self._client.request("POST", f"/api/v1/projects/{project_id}/pubsub/broadcast", json={"channel": channel, "payloadJson": payload})
        return PubSubMessage.from_dict(resp)
