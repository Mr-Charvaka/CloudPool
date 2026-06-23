"""Data models for authentication and account management."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Optional


@dataclass
class User:
    """Represents a CloudPool user account.

    Attributes:
        id: Unique user identifier.
        email: User email address.
        name: Display name.
        role: Access role (e.g., "admin", "developer").
        storage_quota: Maximum storage in bytes.
        current_usage: Current storage usage in bytes.
        google_refresh_token: Whether Google Drive OAuth is linked.
        created_at: Account creation timestamp.
    """

    id: str
    email: str
    name: str
    role: str
    storage_quota: int = 0
    current_usage: int = 0
    google_refresh_token: bool = False
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "User":
        return cls(
            id=d["id"],
            email=d.get("email", ""),
            name=d.get("name", ""),
            role=d.get("role", ""),
            storage_quota=d.get("storageQuota", 0),
            current_usage=d.get("currentUsage", 0),
            google_refresh_token=bool(d.get("googleRefreshToken")),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class AuthTokens:
    """JWT authentication tokens.

    Attributes:
        token: The primary JWT access token.
        refresh_token: Token used to obtain a new access token.
        expires_in: Token lifetime in seconds.
    """

    token: str
    refresh_token: str
    expires_in: int = 3600

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "AuthTokens":
        return cls(
            token=d["token"],
            refresh_token=d.get("refreshToken", d.get("refresh_token", "")),
            expires_in=d.get("expiresIn", d.get("expires_in", 3600)),
        )


@dataclass
class ApiKey:
    """An API key for programmatic access.

    Attributes:
        id: Unique key identifier.
        name: Human-readable key name.
        key_hash: Hashed key value (for display).
        key_prefix: First few characters of the key.
        is_active: Whether the key is currently active.
        created_at: Creation timestamp.
        expires_at: Expiration timestamp, if set.
    """

    id: str
    name: str
    key_hash: str = ""
    key_prefix: str = ""
    is_active: bool = True
    created_at: str = ""
    expires_at: Optional[str] = None

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ApiKey":
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            key_hash=d.get("keyHash", ""),
            key_prefix=d.get("keyPrefix", ""),
            is_active=d.get("active", True),
            created_at=d.get("createdAt", ""),
            expires_at=d.get("expiresAt"),
        )


@dataclass
class ApiKeyAnalytics:
    """Usage analytics for an API key.

    Attributes:
        key_id: The API key identifier.
        key_name: The API key name.
        total_requests: Total request count.
        success_count: Successful request count.
        error_count: Failed request count.
        avg_response_time_ms: Average response time in milliseconds.
    """

    key_id: str
    key_name: str
    total_requests: int = 0
    success_count: int = 0
    error_count: int = 0
    avg_response_time_ms: float = 0.0

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ApiKeyAnalytics":
        return cls(
            key_id=d.get("keyId", ""),
            key_name=d.get("keyName", ""),
            total_requests=d.get("totalRequests", 0),
            success_count=d.get("successCount", 0),
            error_count=d.get("errorCount", 0),
            avg_response_time_ms=d.get("avgResponseTimeMs", 0.0),
        )


@dataclass
class Project:
    """A CloudPool project that groups resources.

    Attributes:
        id: Unique project identifier.
        name: Project name.
        description: Optional project description.
        created_at: Creation timestamp.
    """

    id: str
    name: str
    description: str = ""
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Project":
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            description=d.get("description", ""),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class Secret:
    """A project secret (key-value pair).

    Attributes:
        id: Unique secret identifier.
        key: The secret key name.
        created_at: Creation timestamp.
    """

    id: str
    key: str
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Secret":
        return cls(
            id=d["id"],
            key=d.get("key", ""),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class DatabaseConnection:
    """An external database connection.

    Attributes:
        id: Unique connection identifier.
        db_type: Database type (e.g., "postgres", "mysql").
        host: Database hostname.
        port: Database port.
        database_name: Database name.
        username: Database username.
        active: Whether the connection is active.
    """

    id: str
    db_type: str
    host: str
    port: int
    database_name: str
    username: str
    active: bool = False

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "DatabaseConnection":
        return cls(
            id=d["id"],
            db_type=d.get("dbType", ""),
            host=d.get("host", ""),
            port=d.get("port", 0),
            database_name=d.get("databaseName", ""),
            username=d.get("username", ""),
            active=d.get("active", False),
        )


@dataclass
class Snapshot:
    """A database snapshot.

    Attributes:
        id: Unique snapshot identifier.
        name: Snapshot name.
        created_at: Creation timestamp.
    """

    id: str
    name: str
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Snapshot":
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            created_at=d.get("createdAt", ""),
        )
