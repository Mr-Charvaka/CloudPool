"""Credential providers chain for multi-source authentication.

Resolution order:
  1. Explicit constructor args
  2. Environment variables
  3. Credential file (~/.cloudpool/credentials.json)
  4. OS keychain (platform-specific)
"""

from __future__ import annotations

import json
import os
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Dict, Optional, Tuple


class CredentialProvider(ABC):
    """Abstract base for a credential source."""

    @abstractmethod
    def get_credentials(self) -> Tuple[Optional[str], Optional[str]]:
        """Return (jwt_token, api_key) pair, either of which may be None."""
        ...


class EnvCredentialProvider(CredentialProvider):
    """Reads credentials from CLOUDPOOL_JWT_TOKEN and CLOUDPOOL_API_KEY env vars."""

    def get_credentials(self) -> Tuple[Optional[str], Optional[str]]:
        return (
            os.environ.get("CLOUDPOOL_JWT_TOKEN"),
            os.environ.get("CLOUDPOOL_API_KEY"),
        )


class FileCredentialProvider(CredentialProvider):
    """Reads credentials from ~/.cloudpool/credentials.json."""

    def __init__(self, path: Optional[Path] = None) -> None:
        self._path = path or Path.home() / ".cloudpool" / "credentials.json"

    def get_credentials(self) -> Tuple[Optional[str], Optional[str]]:
        if not self._path.exists():
            return None, None
        try:
            with open(self._path) as f:
                data = json.load(f)
            return data.get("jwt_token"), data.get("api_key")
        except (json.JSONDecodeError, OSError):
            return None, None


class KeychainCredentialProvider(CredentialProvider):
    """Reads credentials from the OS keychain via the 'keyring' package."""

    SERVICE_NAME = "cloudpool"

    def get_credentials(self) -> Tuple[Optional[str], Optional[str]]:
        try:
            import keyring
            jwt_token = keyring.get_password(self.SERVICE_NAME, "jwt")
            api_key = keyring.get_password(self.SERVICE_NAME, "api_key")
            return jwt_token, api_key
        except ImportError:
            return None, None
        except Exception:
            return None, None


class CredentialChain:
    """Chain of credential providers tried in order.

    The first provider that returns a credential wins.
    """

    def __init__(self, providers: Optional[list[CredentialProvider]] = None) -> None:
        self._providers = providers or [
            EnvCredentialProvider(),
            FileCredentialProvider(),
            KeychainCredentialProvider(),
        ]

    def resolve(self) -> Dict[str, Optional[str]]:
        """Resolve credentials by trying each provider in order.

        Returns:
            Dict with keys 'jwt_token' and 'api_key'.
        """
        result: Dict[str, Optional[str]] = {"jwt_token": None, "api_key": None}
        for provider in self._providers:
            jwt_token, api_key = provider.get_credentials()
            if jwt_token and not result["jwt_token"]:
                result["jwt_token"] = jwt_token
            if api_key and not result["api_key"]:
                result["api_key"] = api_key
            if result["jwt_token"] and result["api_key"]:
                break
        return result

    def _write_secure_json(self, path: Path, data: Dict[str, str]) -> None:
        import tempfile
        import os
        fd, temp_path = tempfile.mkstemp(dir=path.parent, text=True)
        try:
            with os.fdopen(fd, 'w') as f:
                json.dump(data, f, indent=2)
            os.chmod(temp_path, 0o600)
            os.replace(temp_path, path)
        except Exception:
            os.unlink(temp_path)
            raise

    def save_jwt(self, token: str) -> None:
        """Persist a JWT token to the credential file.

        Args:
            token: The JWT token string.
        """
        path = Path.home() / ".cloudpool" / "credentials.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        data: Dict[str, str] = {}
        if path.exists():
            try:
                data = json.loads(path.read_text())
            except (json.JSONDecodeError, OSError):
                pass
        data["jwt_token"] = token
        self._write_secure_json(path, data)

    def save_api_key(self, key: str) -> None:
        """Persist an API key to the credential file.

        Args:
            key: The API key string.
        """
        path = Path.home() / ".cloudpool" / "credentials.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        data: Dict[str, str] = {}
        if path.exists():
            try:
                data = json.loads(path.read_text())
            except (json.JSONDecodeError, OSError):
                pass
        data["api_key"] = key
        self._write_secure_json(path, data)

    def clear(self) -> None:
        """Remove stored credentials from file."""
        path = Path.home() / ".cloudpool" / "credentials.json"
        if path.exists():
            path.unlink()
