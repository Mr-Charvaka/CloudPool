"""Shared fixtures and configuration for tests."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, Generator
from unittest.mock import MagicMock, Mock, patch

import pytest

from cloudpool._client import CloudPoolClient
from cloudpool._config import CloudPoolConfig
from cloudpool._credential import CredentialChain


@pytest.fixture
def config() -> CloudPoolConfig:
    return CloudPoolConfig(
        base_url="https://api.cloudpool.dev",
        jwt_token="test-jwt-token",
        timeout=30,
        max_retries=0,
    )


@pytest.fixture
def config_with_api_key() -> CloudPoolConfig:
    return CloudPoolConfig(
        base_url="https://api.cloudpool.dev",
        api_key="test-api-key",
        timeout=30,
        max_retries=0,
    )


@pytest.fixture
def mock_credential_chain() -> CredentialChain:
    chain = CredentialChain()
    chain._providers = []
    return chain


@pytest.fixture
def client(config: CloudPoolConfig, mock_credential_chain: CredentialChain) -> CloudPoolClient:
    return CloudPoolClient(config, mock_credential_chain)


@pytest.fixture
def mock_response() -> MagicMock:
    resp = MagicMock()
    resp.status_code = 200
    resp.ok = True
    resp.text = ""
    resp.headers = {}
    resp.json.return_value = {"status": "ok"}
    resp.content = b'{"status": "ok"}'
    return resp


@pytest.fixture
def sample_file_metadata() -> Dict[str, Any]:
    return {
        "id": "file-123",
        "name": "photo.jpg",
        "originalName": "photo.jpg",
        "size": 1024,
        "mimeType": "image/jpeg",
        "extension": ".jpg",
        "bucket": {"name": "default"},
        "driveLocation": "",
        "checksum": "abc123",
        "public": False,
        "createdAt": "2024-01-01T00:00:00Z",
    }


@pytest.fixture
def sample_auth_tokens() -> Dict[str, Any]:
    return {
        "token": "jwt-token-here",
        "refreshToken": "refresh-token-here",
        "expiresIn": 3600,
    }


@pytest.fixture
def sample_user() -> Dict[str, Any]:
    return {
        "id": "user-123",
        "email": "test@example.com",
        "name": "Test User",
        "role": "developer",
        "storageQuota": 1073741824,
        "currentUsage": 524288,
    }


@pytest.fixture
def temp_dir() -> Generator[Path, None, None]:
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        yield Path(d)
