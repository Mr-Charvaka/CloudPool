"""Payments and billing service."""

from __future__ import annotations

from typing import Any, Dict, List

from cloudpool._client import CloudPoolClient
from cloudpool.models.payments import ChargeResult, GatewayStats, PaymentGateway, Transaction


class PaymentsClient:
    """Synchronous payments client.

    Manages payment gateways, charges, and transactions.

    Accessed via ``cloudpool.payments`` on a ``CloudPool`` instance.
    """

    def __init__(self, client: CloudPoolClient) -> None:
        self._client = client

    def list_gateways(self) -> List[PaymentGateway]:
        """List registered payment gateways.

        Returns:
            List of PaymentGateway objects.
        """
        resp = self._client.request("GET", "/api/dev/payments/gateways")
        return [PaymentGateway.from_dict(g) for g in (resp if isinstance(resp, list) else [])]

    def register_gateway(
        self,
        display_name: str,
        provider: str,
        mode: str = "test",
        api_key: str = "",
        secret_key: str = "",
        webhook_secret: str = "",
        custom_base_url: str = "",
    ) -> PaymentGateway:
        """Register a payment gateway.

        Args:
            display_name: Human-readable name.
            provider: Provider (e.g., "stripe", "paypal").
            mode: "test" or "live".
            api_key: Provider API key.
            secret_key: Provider secret key.
            webhook_secret: Webhook signing secret.
            custom_base_url: Custom API base URL.

        Returns:
            The registered PaymentGateway.
        """
        body: Dict[str, Any] = {
            "displayName": display_name,
            "provider": provider,
            "mode": mode,
            "apiKey": api_key,
            "secretKey": secret_key,
        }
        if webhook_secret:
            body["webhookSecret"] = webhook_secret
        if custom_base_url:
            body["customBaseUrl"] = custom_base_url
        resp = self._client.request(
            "POST", "/api/dev/payments/gateways", json=body,
        )
        return PaymentGateway.from_dict(resp)

    def delete_gateway(self, gateway_id: str) -> None:
        """Delete a payment gateway.

        Args:
            gateway_id: The gateway's unique identifier.
        """
        self._client.request("DELETE", f"/api/dev/payments/gateways/{gateway_id}")

    def create_charge(
        self,
        gateway_id: str,
        amount: int,
        currency: str = "USD",
        description: str = "",
    ) -> ChargeResult:
        """Create a charge.

        Args:
            gateway_id: The payment gateway to use.
            amount: Amount in smallest currency unit (cents).
            currency: Currency code (e.g., "USD").
            description: Optional charge description.

        Returns:
            ChargeResult with status.
        """
        body: Dict[str, Any] = {"amount": amount, "currency": currency}
        if description:
            body["description"] = description
        resp = self._client.request(
            "POST", f"/api/dev/payments/gateways/{gateway_id}/charge",
            json=body,
        )
        return ChargeResult.from_dict(resp)

    def get_transactions(
        self,
        gateway_id: str,
        page: int = 0,
        size: int = 20,
    ) -> List[Transaction]:
        """List transactions for a gateway.

        Args:
            gateway_id: The gateway's unique identifier.
            page: Page number (zero-indexed).
            size: Items per page.

        Returns:
            List of Transaction objects.
        """
        resp = self._client.request(
            "GET", f"/api/dev/payments/gateways/{gateway_id}/transactions",
            params={"page": page, "size": size},
        )
        return [Transaction.from_dict(t) for t in (resp if isinstance(resp, list) else [])]

    def get_gateway_stats(self, gateway_id: str) -> GatewayStats:
        """Get aggregated statistics for a gateway.

        Args:
            gateway_id: The gateway's unique identifier.

        Returns:
            GatewayStats with transaction counts and revenue.
        """
        resp = self._client.request(
            "GET", f"/api/dev/payments/gateways/{gateway_id}/stats",
        )
        return GatewayStats.from_dict(resp)


class AsyncPaymentsClient:
    """Asynchronous payments client."""

    def __init__(self, client: Any) -> None:
        self._client = client

    async def list_gateways(self) -> List[PaymentGateway]:
        resp = await self._client.request("GET", "/api/dev/payments/gateways")
        return [PaymentGateway.from_dict(g) for g in (resp if isinstance(resp, list) else [])]

    async def register_gateway(self, display_name: str, provider: str, mode: str = "test") -> PaymentGateway:
        resp = await self._client.request("POST", "/api/dev/payments/gateways", json={
            "displayName": display_name, "provider": provider, "mode": mode,
        })
        return PaymentGateway.from_dict(resp)

    async def create_charge(self, gateway_id: str, amount: int, currency: str = "USD") -> ChargeResult:
        resp = await self._client.request("POST", f"/api/dev/payments/gateways/{gateway_id}/charge", json={"amount": amount, "currency": currency})
        return ChargeResult.from_dict(resp)

    async def get_transactions(self, gateway_id: str, page: int = 0, size: int = 20) -> List[Transaction]:
        resp = await self._client.request("GET", f"/api/dev/payments/gateways/{gateway_id}/transactions", params={"page": page, "size": size})
        return [Transaction.from_dict(t) for t in (resp if isinstance(resp, list) else [])]
