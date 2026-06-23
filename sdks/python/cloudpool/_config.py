"""Configuration management with 12-factor app principles.

Resolution order:
  1. Constructor arguments (highest priority)
  2. Environment variables (CLOUDPOOL_*)
  3. .env file in CWD or parent directories
  4. Config file (~/.cloudpool/config.yaml)
  5. Defaults (lowest priority)
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Optional

from cloudpool.exceptions import ConfigurationError

DEFAULT_BASE_URL = "https://api.cloudpool.dev"
DEFAULT_MAX_RETRY_SLEEP = 120  # cap on Retry-After sleep (seconds)
ENV_PREFIX = "CLOUDPOOL"


@dataclass(frozen=True)
class CloudPoolConfig:
    """Immutable configuration snapshot for the SDK.

    Attributes:
        base_url: API base URL.
        api_key: API key for authentication.
        jwt_token: JWT token for authentication.
        timeout: Default request timeout in seconds.
        max_retries: Maximum retry attempts.
        verbose: Enable verbose/debug logging.
    """
    base_url: str = DEFAULT_BASE_URL
    api_key: Optional[str] = None
    jwt_token: Optional[str] = None
    timeout: int = 30
    max_retries: int = 3
    max_retry_sleep: int = DEFAULT_MAX_RETRY_SLEEP
    verbose: bool = False

    def validate(self) -> None:
        """Validate configuration consistency.

        Raises:
            ConfigurationError: If both api_key and jwt_token are set,
                or if neither is set.
        """
        if self.api_key and self.jwt_token:
            raise ConfigurationError(
                "Both api_key and jwt_token are set. Use only one authentication method."
            )

    @property
    def auth_header(self) -> Optional[Dict[str, str]]:
        """Return the authentication header dict, or None if unauthenticated."""
        if self.api_key:
            return {"X-API-KEY": self.api_key}
        if self.jwt_token:
            return {"Authorization": f"Bearer {self.jwt_token}"}
        return None


class ConfigLoader:
    """Loads configuration from multiple sources following 12-factor app principles.

    Resolution order (first found wins):
      1. Constructor arguments passed to load()
      2. Environment variables (CLOUDPOOL_*)
      3. .env file in CWD
      4. Config file (~/.cloudpool/config.yaml)
      5. Hardcoded defaults
    """

    def load(
        self,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        jwt_token: Optional[str] = None,
        timeout: Optional[int] = None,
        max_retries: Optional[int] = None,
        max_retry_sleep: Optional[int] = None,
        verbose: Optional[bool] = None,
    ) -> CloudPoolConfig:
        """Load and merge configuration from all sources.

        Args:
            base_url: Override base URL.
            api_key: Override API key.
            jwt_token: Override JWT token.
            timeout: Override timeout.
            max_retries: Override max retries.
            max_retry_sleep: Maximum sleep on Retry-After (caps 429 waits).
            verbose: Override verbose flag.

        Returns:
            A validated CloudPoolConfig instance.

        Raises:
            ConfigurationError: If the resulting config is invalid.
        """
        kwargs: Dict[str, Any] = {}
        self._merge_file_config(kwargs)
        self._merge_env_config(kwargs)
        self._merge_args(kwargs, base_url, api_key, jwt_token, timeout, max_retries, max_retry_sleep, verbose)
        cfg = CloudPoolConfig(**kwargs)
        cfg.validate()
        return cfg

    def _merge_file_config(self, cfg: CloudPoolConfig) -> None:
        """Load from ~/.cloudpool/config.yaml if it exists."""
        config_file = Path.home() / ".cloudpool" / "config.yaml"
        if not config_file.exists():
            return
        try:
            import yaml
            with open(config_file) as f:
                data = yaml.safe_load(f) or {}
        except Exception:
            return

        if isinstance(data, dict):
            self._apply_dict(kwargs, data)

    def _merge_env_config(self, cfg: CloudPoolConfig) -> None:
        """Load from CLOUDPOOL_* environment variables."""
        mapping = {
            f"{ENV_PREFIX}_BASE_URL": "base_url",
            f"{ENV_PREFIX}_API_KEY": "api_key",
            f"{ENV_PREFIX}_JWT_TOKEN": "jwt_token",
            f"{ENV_PREFIX}_TIMEOUT": "timeout",
            f"{ENV_PREFIX}_MAX_RETRIES": "max_retries",
            f"{ENV_PREFIX}_MAX_RETRY_SLEEP": "max_retry_sleep",
            f"{ENV_PREFIX}_VERBOSE": "verbose",
        }
        env_data: Dict[str, str] = {}
        for env_key, cfg_key in mapping.items():
            val = os.environ.get(env_key)
            if val is not None:
                env_data[cfg_key] = val

        # Load from .env file
        self._merge_dotenv(env_data)

        self._apply_dict(kwargs, env_data)

    def _merge_dotenv(self, env_data: Dict[str, str]) -> None:
        """Parse .env file from CWD and merge into env_data."""
        dotenv_path = Path.cwd() / ".env"
        if not dotenv_path.exists():
            # Try parent directories
            for parent in Path.cwd().parents:
                candidate = parent / ".env"
                if candidate.exists():
                    dotenv_path = candidate
                    break
            else:
                return

        try:
            with open(dotenv_path) as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, _, val = line.partition("=")
                    key = key.strip()
                    val = val.strip().strip("\"'")
                    if key.startswith(ENV_PREFIX):
                        cfg_key = key[len(ENV_PREFIX) + 1:].lower()
                        if cfg_key not in env_data:
                            env_data[cfg_key] = val
        except Exception:
            pass

    def _merge_args(
        self,
        kwargs: Dict[str, Any],
        base_url: Optional[str],
        api_key: Optional[str],
        jwt_token: Optional[str],
        timeout: Optional[int],
        max_retries: Optional[int],
        max_retry_sleep: Optional[int],
        verbose: Optional[bool],
    ) -> None:
        """Apply explicit constructor arguments (highest priority)."""
        if base_url is not None:
            kwargs["base_url"] = base_url
        if api_key is not None:
            kwargs["api_key"] = api_key
        if jwt_token is not None:
            kwargs["jwt_token"] = jwt_token
        if timeout is not None:
            kwargs["timeout"] = timeout
        if max_retries is not None:
            kwargs["max_retries"] = max_retries
        if max_retry_sleep is not None:
            kwargs["max_retry_sleep"] = max_retry_sleep
        if verbose is not None:
            kwargs["verbose"] = verbose

    def _apply_dict(self, kwargs: Dict[str, Any], data: Dict[str, str]) -> None:
        """Apply a flat string dict to config kwargs with type coercion."""
        for key, val in data.items():
            if val is None:
                continue
            if key == "base_url" and "base_url" not in kwargs:
                kwargs["base_url"] = str(val)
            elif key in ("api_key", "jwt_token"):
                if key not in kwargs:
                    kwargs[key] = str(val)
            elif key == "timeout" and "timeout" not in kwargs:
                try:
                    kwargs["timeout"] = int(val)
                except (ValueError, TypeError):
                    pass
            elif key == "max_retries" and "max_retries" not in kwargs:
                try:
                    kwargs["max_retries"] = int(val)
                except (ValueError, TypeError):
                    pass
            elif key == "max_retry_sleep" and "max_retry_sleep" not in kwargs:
                try:
                    kwargs["max_retry_sleep"] = int(val)
                except (ValueError, TypeError):
                    pass
            elif key == "verbose" and "verbose" not in kwargs:
                if isinstance(val, str):
                    kwargs["verbose"] = val.lower() in ("1", "true", "yes")
                else:
                    kwargs["verbose"] = bool(val)
