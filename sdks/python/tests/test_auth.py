"""Tests for the auth service client."""

from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest

from cloudpool.models.auth import AuthTokens, User
from cloudpool.services.auth import AuthClient


class TestAuthClient:
    @pytest.fixture
    def mock_client(self):
        return MagicMock()

    @pytest.fixture
    def auth(self, mock_client):
        return AuthClient(mock_client)

    def test_login_returns_tokens(self, auth, mock_client):
        mock_client.request.return_value = {
            "token": "jwt-123",
            "refreshToken": "refresh-456",
            "expiresIn": 3600,
        }
        tokens = auth.login("user@example.com", "password")
        assert isinstance(tokens, AuthTokens)
        assert tokens.token == "jwt-123"
        assert tokens.refresh_token == "refresh-456"
        mock_client.set_jwt_token.assert_called_with("jwt-123")

    def test_register_returns_tokens(self, auth, mock_client):
        mock_client.request.return_value = {
            "token": "jwt-789",
            "refreshToken": "refresh-012",
        }
        tokens = auth.register("new@example.com", "pass", "New User")
        assert isinstance(tokens, AuthTokens)
        assert tokens.token == "jwt-789"
        mock_client.set_jwt_token.assert_called_with("jwt-789")

    def test_me_returns_user(self, auth, mock_client):
        mock_client.request.return_value = {
            "id": "user-1",
            "email": "test@example.com",
            "name": "Test",
            "role": "developer",
            "storageQuota": 1000,
            "currentUsage": 100,
        }
        user = auth.me()
        assert isinstance(user, User)
        assert user.email == "test@example.com"
        assert user.role == "developer"

    def test_logout_clears_token(self, auth, mock_client):
        auth.logout()
        mock_client.request.assert_called_with("POST", "/api/auth/logout")
        assert auth._client.jwt_token is None

    def test_list_api_keys(self, auth, mock_client):
        mock_client.request.return_value = [
            {"id": "key-1", "name": "My Key", "keyHash": "abc", "keyPrefix": "sk..."},
        ]
        keys = auth.list_api_keys()
        assert len(keys) == 1
        assert keys[0].name == "My Key"

    def test_list_projects(self, auth, mock_client):
        mock_client.request.return_value = [
            {"id": "proj-1", "name": "My Project", "description": "desc"},
        ]
        projects = auth.list_projects()
        assert len(projects) == 1
        assert projects[0].name == "My Project"

    def test_generate_api_key(self, auth, mock_client):
        mock_client.request.return_value = {
            "id": "key-new",
            "name": "New Key",
            "key": "sk-abc123",
        }
        result = auth.generate_api_key("New Key", days_to_live=30)
        assert result["key"] == "sk-abc123"
