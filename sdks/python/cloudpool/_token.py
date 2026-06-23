"""JWT token manager with automatic refresh.

Decodes JWT payloads to inspect expiry and proactively refreshes
tokens before they expire.
"""

from __future__ import annotations

import json
import logging
import time
from typing import Any, Callable, Dict, Optional

import jwt

logger = logging.getLogger(__name__)

# Refresh when less than this many seconds remain before expiry
REFRESH_BUFFER_SECONDS = 300  # 5 minutes


class TokenManager:
    """Manages JWT lifecycle: decode, expiry check, proactive refresh.

    Args:
        initial_token: The initial JWT token.
        on_refresh: Callback invoked with the new token string after
            a successful refresh.
    """

    def __init__(
        self,
        initial_token: Optional[str] = None,
        on_refresh: Optional[Callable[[str], None]] = None,
    ) -> None:
        self._token: Optional[str] = initial_token
        self._on_refresh = on_refresh

    @property
    def token(self) -> Optional[str]:
        return self._token

    @token.setter
    def token(self, value: str) -> None:
        self._token = value

    def decode(self, token: Optional[str] = None) -> Optional[Dict[str, Any]]:
        """Decode a JWT token payload without signature verification.

        Args:
            token: The JWT token string. Defaults to current token.

        Returns:
            Decoded payload dict, or None if token is missing/invalid.
        """
        token = token or self._token
        if not token:
            return None
        try:
            return jwt.decode(token, options={"verify_signature": False})
        except jwt.PyJWTError as e:
            logger.warning("Failed to decode JWT payload: %s", e)
            return None

    def is_expired(self, token: Optional[str] = None) -> bool:
        """Check if the token is expired or will expire soon.

        Args:
            token: The JWT token string. Defaults to current token.

        Returns:
            True if the token is missing, expired, or within the refresh buffer.
        """
        payload = self.decode(token)
        if payload is None:
            return True
        exp = payload.get("exp")
        if exp is None:
            logger.debug("JWT has no exp claim, assuming valid")
            return False
        return time.time() >= (exp - REFRESH_BUFFER_SECONDS)

    def needs_refresh(self) -> bool:
        """Check if the token needs proactive refresh.

        Returns:
            True if the token is expired or within the refresh buffer window.
        """
        return self.is_expired()

    def handle_refresh_response(self, new_token: str) -> None:
        """Handle a successful token refresh response.

        Args:
            new_token: The new JWT token string.
        """
        old = self._token
        self._token = new_token
        if self._on_refresh:
            try:
                self._on_refresh(new_token)
            except Exception as e:
                logger.warning("Token refresh callback failed: %s", e)
        logger.debug("JWT token refreshed (was set: %s)", old is not None)
