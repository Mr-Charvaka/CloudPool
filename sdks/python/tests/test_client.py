"""Tests for the sync HTTP client."""

from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest
import requests

from cloudpool._client import CloudPoolClient
from cloudpool._config import CloudPoolConfig
from cloudpool.exceptions import (
    AuthenticationError,
    CloudPoolError,
    NotFoundError,
    RateLimitError,
    ValidationError,
)


class TestCloudPoolClient:
    def test_init_with_jwt(self, config: CloudPoolConfig):
        client = CloudPoolClient(config)
        assert client.jwt_token == "test-jwt-token"
        assert client.api_key is None

    def test_init_with_api_key(self, config_with_api_key: CloudPoolConfig):
        client = CloudPoolClient(config_with_api_key)
        assert client.api_key == "test-api-key"
        assert client.jwt_token is None

    def test_set_jwt_token(self, client: CloudPoolClient):
        client.set_jwt_token("new-token")
        assert client.jwt_token == "new-token"
        assert client.api_key is None

    def test_set_api_key(self, client: CloudPoolClient):
        client.set_api_key("new-key")
        assert client.api_key == "new-key"
        assert client.jwt_token is None

    def test_build_headers_with_jwt(self, client: CloudPoolClient):
        headers = client._build_headers()
        assert headers.get("Authorization") == "Bearer test-jwt-token"
        assert "X-API-KEY" not in headers

    def test_build_headers_with_api_key(self, config_with_api_key: CloudPoolConfig):
        client = CloudPoolClient(config_with_api_key)
        headers = client._build_headers()
        assert headers.get("X-API-KEY") == "test-api-key"
        assert "Authorization" not in headers

    def test_build_url(self, client: CloudPoolClient):
        url = client._build_url("/api/test")
        assert url == "https://api.cloudpool.dev/api/test"

    def test_build_url_leading_slash(self, client: CloudPoolClient):
        url = client._build_url("api/test")
        assert url == "https://api.cloudpool.dev/api/test"

    def test_handle_response_200(self, client: CloudPoolClient):
        resp = MagicMock(spec=requests.Response)
        resp.status_code = 200
        resp.ok = True
        resp.json.return_value = {"key": "value"}
        result = client._handle_response(resp)
        assert result == {"key": "value"}

    def test_handle_response_204(self, client: CloudPoolClient):
        resp = MagicMock(spec=requests.Response)
        resp.status_code = 204
        resp.ok = True
        result = client._handle_response(resp)
        assert result is None

    def test_handle_response_401(self, client: CloudPoolClient):
        resp = MagicMock(spec=requests.Response)
        resp.status_code = 401
        resp.ok = False
        resp.text = "unauthorized"
        with pytest.raises(AuthenticationError):
            client._handle_response(resp)

    def test_handle_response_404(self, client: CloudPoolClient):
        resp = MagicMock(spec=requests.Response)
        resp.status_code = 404
        resp.ok = False
        resp.text = "not found"
        with pytest.raises(NotFoundError):
            client._handle_response(resp)

    def test_handle_response_422(self, client: CloudPoolClient):
        resp = MagicMock(spec=requests.Response)
        resp.status_code = 422
        resp.ok = False
        resp.text = "validation error"
        with pytest.raises(ValidationError):
            client._handle_response(resp)

    def test_handle_response_429(self, client: CloudPoolClient):
        resp = MagicMock(spec=requests.Response)
        resp.status_code = 429
        resp.ok = False
        resp.text = "rate limited"
        resp.headers = {"Retry-After": "5"}
        with pytest.raises(RateLimitError):
            client._handle_response(resp)

    def test_handle_response_500(self, client: CloudPoolClient):
        resp = MagicMock(spec=requests.Response)
        resp.status_code = 500
        resp.ok = False
        resp.text = "server error"
        resp.reason = "Internal Server Error"
        with pytest.raises(CloudPoolError):
            client._handle_response(resp)

    def test_request_success(self, client: CloudPoolClient):
        with patch.object(client._session, "request") as mock_request:
            mock_resp = MagicMock(spec=requests.Response)
            mock_resp.status_code = 200
            mock_resp.ok = True
            mock_resp.json.return_value = {"status": "ok"}
            mock_request.return_value = mock_resp

            result = client.request("GET", "/api/test")
            assert result == {"status": "ok"}
            mock_request.assert_called_once()

    def test_request_with_params(self, client: CloudPoolClient):
        with patch.object(client._session, "request") as mock_request:
            mock_resp = MagicMock(spec=requests.Response)
            mock_resp.status_code = 200
            mock_resp.ok = True
            mock_resp.json.return_value = {}
            mock_request.return_value = mock_resp

            client.request("GET", "/api/test", params={"page": "1"})
            _, kwargs = mock_request.call_args
            assert kwargs["params"] == {"page": "1"}

    def test_request_retry_on_timeout(self, client: CloudPoolClient):
        client.config.max_retries = 1
        with patch.object(client._session, "request") as mock_request:
            mock_request.side_effect = requests.exceptions.Timeout("timeout")
            with pytest.raises(CloudPoolError):
                client.request("GET", "/api/test")

    def test_close(self, client: CloudPoolClient):
        with patch.object(client._session, "close") as mock_close:
            client.close()
            mock_close.assert_called_once()
