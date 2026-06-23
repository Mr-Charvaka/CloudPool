"""Tests for configuration management."""

from __future__ import annotations

import os
from pathlib import Path
from unittest.mock import patch

import pytest

from cloudpool._config import CloudPoolConfig, ConfigLoader
from cloudpool.exceptions import ConfigurationError


class TestCloudPoolConfig:
    def test_defaults(self):
        cfg = CloudPoolConfig()
        assert cfg.base_url == "https://api.cloudpool.dev"
        assert cfg.api_key is None
        assert cfg.jwt_token is None
        assert cfg.timeout == 30
        assert cfg.max_retries == 3
        assert cfg.verbose is False

    def test_auth_header_api_key(self):
        cfg = CloudPoolConfig(api_key="sk-test")
        h = cfg.auth_header
        assert h == {"X-API-KEY": "sk-test"}

    def test_auth_header_jwt(self):
        cfg = CloudPoolConfig(jwt_token="jwt-test")
        h = cfg.auth_header
        assert h == {"Authorization": "Bearer jwt-test"}

    def test_auth_header_none(self):
        cfg = CloudPoolConfig()
        assert cfg.auth_header is None

    def test_validate_both_auth_raises(self):
        cfg = CloudPoolConfig(api_key="key", jwt_token="jwt")
        with pytest.raises(ConfigurationError):
            cfg.validate()

    def test_validate_single_auth_ok(self):
        cfg = CloudPoolConfig(api_key="key")
        cfg.validate()  # should not raise

        cfg2 = CloudPoolConfig(jwt_token="jwt")
        cfg2.validate()  # should not raise

    def test_validate_no_auth_ok(self):
        cfg = CloudPoolConfig()
        cfg.validate()  # should not raise (credentials can be set later)


class TestConfigLoader:
    def test_load_defaults(self):
        loader = ConfigLoader()
        cfg = loader.load()
        assert cfg.base_url == "https://api.cloudpool.dev"

    def test_load_with_args(self):
        loader = ConfigLoader()
        cfg = loader.load(
            base_url="https://custom.example.com",
            api_key="sk-custom",
            timeout=60,
        )
        assert cfg.base_url == "https://custom.example.com"
        assert cfg.api_key == "sk-custom"
        assert cfg.timeout == 60

    def test_load_env_vars(self):
        loader = ConfigLoader()
        with patch.dict(os.environ, {
            "CLOUDPOOL_BASE_URL": "https://env.example.com",
            "CLOUDPOOL_API_KEY": "sk-env",
            "CLOUDPOOL_TIMEOUT": "45",
        }, clear=True):
            cfg = loader.load()
            assert cfg.base_url == "https://env.example.com"
            assert cfg.api_key == "sk-env"
            assert cfg.timeout == 45

    def test_args_override_env(self):
        loader = ConfigLoader()
        with patch.dict(os.environ, {
            "CLOUDPOOL_BASE_URL": "https://env.example.com",
        }, clear=True):
            cfg = loader.load(base_url="https://args.example.com")
            assert cfg.base_url == "https://args.example.com"

    def test_verbose_env_var(self):
        loader = ConfigLoader()
        with patch.dict(os.environ, {
            "CLOUDPOOL_VERBOSE": "true",
        }, clear=True):
            cfg = loader.load()
            assert cfg.verbose is True

    def test_max_retries_env_var(self):
        loader = ConfigLoader()
        with patch.dict(os.environ, {
            "CLOUDPOOL_MAX_RETRIES": "5",
        }, clear=True):
            cfg = loader.load()
            assert cfg.max_retries == 5
