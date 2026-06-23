"""Exception hierarchy for the CloudPool SDK.

All exceptions inherit from CloudPoolError, allowing callers to catch
a single base type or specific subtypes.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional


class CloudPoolError(Exception):
    """Base exception for all CloudPool SDK errors.

    Attributes:
        message: Human-readable error description.
        status_code: HTTP status code, if applicable.
        body: Raw response body, if available.
    """

    def __init__(
        self,
        message: str,
        status_code: Optional[int] = None,
        body: Optional[str] = None,
    ) -> None:
        super().__init__(message)
        self.message: str = message
        self.status_code: Optional[int] = status_code
        self.body: Optional[str] = body

    def __str__(self) -> str:
        parts = [self.message]
        if self.status_code is not None:
            parts.insert(0, f"[{self.status_code}]")
        return " ".join(parts)


class AuthenticationError(CloudPoolError):
    """Raised when authentication fails or credentials are invalid/expired."""


class RateLimitError(CloudPoolError):
    """Raised when the API rate limit has been exceeded.

    Check the 'retry_after' attribute for the suggested wait time.
    """

    def __init__(
        self,
        message: str,
        status_code: Optional[int] = None,
        body: Optional[str] = None,
        retry_after: Optional[int] = None,
    ) -> None:
        super().__init__(message, status_code, body)
        self.retry_after: Optional[int] = retry_after


class NotFoundError(CloudPoolError):
    """Raised when a requested resource does not exist."""


class ValidationError(CloudPoolError):
    """Raised when request validation fails (HTTP 422)."""


class ConflictError(CloudPoolError):
    """Raised when a resource conflict occurs (HTTP 409)."""


class GraphQLError(CloudPoolError):
    """Raised when a GraphQL query returns errors.

    Attributes:
        errors: The list of GraphQL error dicts from the response.
    """

    def __init__(
        self,
        message: str,
        errors: Optional[List[Dict[str, Any]]] = None,
    ) -> None:
        super().__init__(message, status_code=400)
        self.message = message
        self.errors: List[Dict[str, Any]] = errors or []


class ConfigurationError(CloudPoolError):
    """Raised when the SDK configuration is invalid."""


class ConnectionError(CloudPoolError):
    """Raised when a network-level connection failure occurs."""
