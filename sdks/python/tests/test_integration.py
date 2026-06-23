"""Integration-style tests using a mock HTTP server (responses library).

These tests verify the full request/response lifecycle without a real backend.
"""

from __future__ import annotations

import json
from unittest.mock import patch

import pytest
import responses

from cloudpool import CloudPool
from cloudpool.exceptions import (
    AuthenticationError,
    NotFoundError,
    RateLimitError,
    ValidationError,
)

BASE_URL = "https://api.cloudpool.dev"


@pytest.fixture
def cp() -> CloudPool:
    return CloudPool(base_url=BASE_URL, api_key="test-key-12345")


@responses.activate
def test_auth_me_success(cp: CloudPool) -> None:
    responses.get(
        f"{BASE_URL}/api/auth/me",
        json={
            "id": "u-001",
            "email": "test@example.com",
            "name": "Test User",
            "role": "developer",
            "active": True,
            "createdAt": "2024-01-01T00:00:00Z",
        },
        status=200,
    )
    user = cp.auth.me()
    assert user.id == "u-001"
    assert user.email == "test@example.com"
    assert user.name == "Test User"
    assert user.role == "developer"
    assert user.active is True


@responses.activate
def test_auth_login_success(cp: CloudPool) -> None:
    responses.post(
        f"{BASE_URL}/api/auth/login",
        json={
            "token": "jwt-abc-123",
            "refreshToken": "refresh-xyz-789",
            "user": {
                "id": "u-001",
                "email": "test@example.com",
                "name": "Test User",
                "role": "developer",
                "active": True,
                "createdAt": "2024-01-01T00:00:00Z",
            },
        },
        status=200,
    )
    result = cp.auth.login("test@example.com", "password123")
    assert result.token == "jwt-abc-123"
    assert result.refresh_token == "refresh-xyz-789"
    assert result.user.email == "test@example.com"


@responses.activate
def test_files_upload(cp: CloudPool) -> None:
    responses.post(
        f"{BASE_URL}/api/files/upload",
        json={
            "id": "f-001",
            "originalName": "test.txt",
            "size": 1024,
            "mimeType": "text/plain",
            "checksum": "abc123",
            "bucket": "default",
        },
        status=200,
    )
    # Use a tiny byte payload to simulate file upload
    meta = cp.files.upload(b"hello world", filename="test.txt")
    assert meta.id == "f-001"
    assert meta.original_name == "test.txt"
    assert meta.size == 1024


@responses.activate
def test_files_list(cp: CloudPool) -> None:
    responses.get(
        f"{BASE_URL}/api/files",
        json=[
            {
                "id": "f-001",
                "originalName": "a.txt",
                "size": 100,
                "mimeType": "text/plain",
                "checksum": "aaa",
                "bucket": "default",
                "isPublic": False,
                "createdAt": "2024-01-01T00:00:00Z",
            },
            {
                "id": "f-002",
                "originalName": "b.txt",
                "size": 200,
                "mimeType": "text/plain",
                "checksum": "bbb",
                "bucket": "default",
                "isPublic": True,
                "createdAt": "2024-01-01T00:00:00Z",
            },
        ],
        status=200,
    )
    files = cp.files.list()
    assert len(files) == 2
    assert files[0].original_name == "a.txt"
    assert files[1].is_public is True


@responses.activate
def test_database_list_tables(cp: CloudPool) -> None:
    responses.get(
        f"{BASE_URL}/api/db/tables",
        json=[
            {
                "id": "t-001",
                "name": "users",
                "displayName": "Users",
                "description": "User profiles",
                "fields": [
                    {"fieldName": "name", "fieldType": "String", "required": True},
                ],
                "createdAt": "2024-01-01T00:00:00Z",
            },
        ],
        status=200,
    )
    tables = cp.database.list_tables()
    assert len(tables) == 1
    assert tables[0].name == "users"
    assert tables[0].fields[0].field_name == "name"
    assert tables[0].fields[0].required is True


@responses.activate
def test_rate_limit_retry(cp: CloudPool) -> None:
    """Verify that 429 triggers retry logic and eventually succeeds."""
    responses.get(
        f"{BASE_URL}/api/auth/me",
        json={"error": "rate limited"},
        status=429,
    )
    responses.get(
        f"{BASE_URL}/api/auth/me",
        json={
            "id": "u-002",
            "email": "retry@example.com",
            "name": "Retry User",
            "role": "developer",
            "active": True,
            "createdAt": "2024-01-01T00:00:00Z",
        },
        status=200,
    )
    # With max_retries=1, should succeed on second attempt
    cp2 = CloudPool(base_url=BASE_URL, api_key="test-key", max_retries=1)
    user = cp2.auth.me()
    assert user.email == "retry@example.com"


@responses.activate
def test_not_found_error(cp: CloudPool) -> None:
    responses.get(
        f"{BASE_URL}/api/files/download/nonexistent",
        json={"error": "File not found", "detail": "No file with id nonexistent"},
        status=404,
    )
    with pytest.raises(NotFoundError) as exc:
        cp.files.download("nonexistent")
    assert "404" in str(exc.value)


@responses.activate
def test_authentication_error(cp: CloudPool) -> None:
    responses.get(
        f"{BASE_URL}/api/auth/me",
        json={"error": "Invalid or expired token"},
        status=401,
    )
    with pytest.raises(AuthenticationError):
        cp.auth.me()


@responses.activate
def test_graphql_query(cp: CloudPool) -> None:
    responses.post(
        f"{BASE_URL}/graphql",
        json={
            "data": {
                "semanticSearch": [
                    {"docId": "d1", "content": "hello world", "score": 0.95},
                ],
            },
        },
        status=200,
    )
    result = cp._client._graphql(
        "query Search($q: String!) { semanticSearch(query: $q) { docId content score } }",
        {"q": "hello"},
    )
    assert result.data["semanticSearch"][0]["docId"] == "d1"


@responses.activate
def test_streaming_download(cp: CloudPool) -> None:
    """Verify streaming download writes chunks directly to disk."""
    import tempfile
    from pathlib import Path

    content = b"x" * 65536 * 3  # 192 KiB — spans multiple chunks
    responses.get(
        f"{BASE_URL}/api/files/download/f-003",
        body=content,
        status=200,
        content_type="application/octet-stream",
    )

    with tempfile.TemporaryDirectory() as tmp:
        dest = Path(tmp) / "output.bin"
        result = cp.files.download_to_path("f-003", destination=str(dest))
        assert str(dest) == result
        assert dest.read_bytes() == content


@responses.activate
def test_validation_error(cp: CloudPool) -> None:
    responses.post(
        f"{BASE_URL}/api/db/tables",
        json={
            "error": "Validation failed",
            "detail": "Field 'name' must not be empty",
        },
        status=422,
    )
    with pytest.raises(ValidationError):
        cp.database.create_table("", "Empty", "", [])
