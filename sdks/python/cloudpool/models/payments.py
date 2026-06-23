"""Data models for payments and billing."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class PaymentGateway:
    """A registered payment gateway.

    Attributes:
        id: Unique gateway identifier.
        display_name: Human-readable name.
        provider: Provider name (e.g., "stripe", "paypal").
        mode: Operating mode ("test" or "live").
        is_active: Whether the gateway is active.
        created_at: Registration timestamp.
    """

    id: str
    display_name: str = ""
    provider: str = ""
    mode: str = "test"
    is_active: bool = False
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "PaymentGateway":
        return cls(
            id=d["id"],
            display_name=d.get("displayName", ""),
            provider=d.get("provider", ""),
            mode=d.get("mode", "test"),
            is_active=d.get("active", d.get("isActive", False)),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class ChargeResult:
    """Result of a charge operation.

    Attributes:
        id: Unique charge identifier.
        gateway_id: The gateway used.
        amount: Charge amount in smallest currency unit.
        currency: Currency code (e.g., "USD").
        status: Charge status (e.g., "succeeded", "failed").
        provider_transaction_id: Provider-side transaction ID.
        created_at: Charge timestamp.
    """

    id: str
    gateway_id: str = ""
    amount: int = 0
    currency: str = "USD"
    status: str = ""
    provider_transaction_id: Optional[str] = None
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ChargeResult":
        return cls(
            id=d.get("id", ""),
            gateway_id=d.get("gatewayId", ""),
            amount=d.get("amount", 0),
            currency=d.get("currency", "USD"),
            status=d.get("status", ""),
            provider_transaction_id=d.get("providerTransactionId"),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class Transaction:
    """A payment transaction record.

    Attributes:
        id: Unique transaction identifier.
        gateway_id: The gateway used.
        amount: Transaction amount.
        currency: Currency code.
        status: Transaction status.
        provider_transaction_id: Provider-side transaction ID.
        created_at: Transaction timestamp.
    """

    id: str
    gateway_id: str = ""
    amount: int = 0
    currency: str = "USD"
    status: str = ""
    provider_transaction_id: Optional[str] = None
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Transaction":
        return cls(
            id=d.get("id", ""),
            gateway_id=d.get("gatewayId", ""),
            amount=d.get("amount", 0),
            currency=d.get("currency", "USD"),
            status=d.get("status", ""),
            provider_transaction_id=d.get("providerTransactionId"),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class GatewayStats:
    """Aggregated statistics for a payment gateway.

    Attributes:
        total_transactions: Total transaction count.
        total_revenue: Total revenue processed.
        success_rate: Fraction of successful transactions (0.0-1.0).
        currency: Currency for monetary values.
    """

    total_transactions: int = 0
    total_revenue: float = 0.0
    success_rate: float = 0.0
    currency: str = "USD"

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "GatewayStats":
        return cls(
            total_transactions=d.get("totalTransactions", 0),
            total_revenue=d.get("totalRevenue", 0.0),
            success_rate=d.get("successRate", 0.0),
            currency=d.get("currency", "USD"),
        )
