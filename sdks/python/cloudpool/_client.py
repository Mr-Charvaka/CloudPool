"""Synchronous HTTP client for the CloudPool API."""

from __future__ import annotations

import io
import logging
import time
from pathlib import Path
from typing import Any, Callable, Dict, Iterator, Optional, Tuple, Union

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from cloudpool._base_client import BaseCloudPoolClient
from cloudpool._config import CloudPoolConfig
from cloudpool._credential import CredentialChain
from cloudpool._utils import build_user_agent, normalize_file_input
from cloudpool.models.graphql import GraphQLResponse
from cloudpool.exceptions import (
    AuthenticationError,
    CloudPoolError,
    ConflictError,
    ConnectionError,
    NotFoundError,
    RateLimitError,
    ValidationError,
)

logger = logging.getLogger(__name__)


class CloudPoolClient(BaseCloudPoolClient):
    """Synchronous HTTP client with retry, connection pooling, and progress.

    Typically not instantiated directly — use CloudPool() factory.
    """

    def __init__(
        self,
        config: CloudPoolConfig,
        credential_provider: Optional[CredentialChain] = None,
    ) -> None:
        super().__init__(config, credential_provider)

        self._session = requests.Session()

        # Transport-layer retry: handles DNS / connection / 5xx blips
        # before our application-level retry loop ever sees the request.
        retry_strategy = Retry(
            total=3,
            connect=3,
            read=2,
            status=2,
            status_forcelist={500, 502, 503, 504},
            backoff_factor=0.5,
            allowed_methods=frozenset({"GET", "POST", "PUT", "DELETE", "PATCH"}),
        )
        adapter = HTTPAdapter(
            pool_connections=25,
            pool_maxsize=50,
            max_retries=retry_strategy,
        )
        self._session.mount("https://", adapter)
        self._session.mount("http://", adapter)

        # Timeouts
        self._timeout: float = float(config.timeout)

        # Request ID for tracing
        self._request_id: int = 0

    def request(
        self,
        method: str,
        path: str,
        *,
        headers: Optional[Dict[str, str]] = None,
        params: Optional[Dict[str, Any]] = None,
        json: Any = None,
        data: Any = None,
        files: Optional[Dict[str, Any]] = None,
        stream: bool = False,
        timeout: Optional[float] = None,
        max_retries: Optional[int] = None,
        on_download_progress: Optional[Callable[[int, int], None]] = None,
    ) -> Any:
        """Execute a synchronous HTTP request with retry and error handling.

        Args:
            method: HTTP method (GET, POST, PUT, DELETE, etc.).
            path: Request path (e.g., "/api/files").
            headers: Additional headers.
            params: Query string parameters.
            json: JSON-serializable request body.
            data: Raw request body.
            files: Multipart file upload dict.
            stream: If True, return raw Response for streaming.
            timeout: Override request timeout.
            max_retries: Override max retries.
            on_download_progress: Callback(bytes_read, total_bytes) during streaming.

        Returns:
            Parsed JSON response (dict or list), raw Response (if stream=True),
            or None (if 204 No Content).

        Raises:
            AuthenticationError: On 401.
            RateLimitError: On 429.
            NotFoundError: On 404.
            ValidationError: On 422.
            ConflictError: On 409.
            CloudPoolError: On other errors.
        """
        url = self._build_url(path)
        request_headers = self._build_headers(headers)

        self._request_id += 1
        request_id = f"cp-{self._request_id}"

        retries = max_retries if max_retries is not None else self.config.max_retries

        for attempt in range(retries + 1):
            try:
                resp = self._session.request(
                    method=method,
                    url=url,
                    headers=request_headers,
                    params=params,
                    json=json,
                    data=data,
                    files=files,
                    stream=stream or on_download_progress is not None,
                    timeout=timeout or self._timeout,
                )

                if stream:
                    if on_download_progress and "content-length" in resp.headers:
                        total = int(resp.headers["content-length"])
                        return _StreamingResponse(resp, total, on_download_progress)
                    return resp

                # Application-level retry: 429 rate limits only.
                # Transport-level retry (connection errors, 5xx) is handled
                # by urllib3's Retry adapter — see __init__.
                if resp.status_code == 429 and attempt < retries:
                    retry_after = _parse_retry_after(resp)
                    capped = min(retry_after, self.config.max_retry_sleep)
                    logger.info(
                        "Rate limited (attempt %d/%d). Retrying after %ds.",
                        attempt + 1, retries + 1, capped,
                    )
                    time.sleep(capped)
                    continue

                return self._handle_response(resp)

            except requests.exceptions.Timeout as e:
                raise ConnectionError(
                    f"Request timed out after {timeout or self._timeout}s: {method} {path}",
                ) from e

            except requests.exceptions.ConnectionError as e:
                raise ConnectionError(
                    f"Connection failed: {e}",
                ) from e

            except requests.exceptions.RequestException as e:
                raise CloudPoolError(f"HTTP request failed: {e}") from e

        return None

    def _build_url(self, path: str) -> str:
        """Build a full URL from a path.

        Args:
            path: The request path (e.g., "/api/files").

        Returns:
            Full URL string.
        """
        base = self.config.base_url.rstrip("/")
        path = path.lstrip("/")
        return f"{base}/{path}"

    def _handle_response(self, resp: requests.Response) -> Any:
        """Process an HTTP response, raising typed exceptions on errors.

        Args:
            resp: The HTTP response.

        Returns:
            Parsed JSON or None for 204.

        Raises:
            AuthenticationError: On 401.
            RateLimitError: On 429.
            NotFoundError: On 404.
            ValidationError: On 422.
            ConflictError: On 409.
            CloudPoolError: On other errors.
        """
        if resp.status_code == 401:
            raise AuthenticationError(
                "Invalid or expired credentials",
                status_code=401,
            )
        if resp.status_code == 429:
            retry_after = _parse_retry_after(resp)
            raise RateLimitError(
                f"Rate limit exceeded. Retry after {retry_after}s.",
                status_code=429,
                retry_after=retry_after,
            )
        if resp.status_code == 404:
            raise NotFoundError(
                "Resource not found",
                status_code=404,
            )
        if resp.status_code == 409:
            raise ConflictError(
                "Resource conflict",
                status_code=409,
            )
        if resp.status_code == 422:
            raise ValidationError(
                "Validation failed",
                status_code=422,
            )
        if resp.status_code == 204:
            return None
        if not resp.ok:
            msg = _extract_error_message(resp)
            raise CloudPoolError(
                f"API error [{resp.status_code}]: {msg}",
                status_code=resp.status_code,
            )
        try:
            return resp.json()
        except ValueError:
            return resp.content

    def upload(
        self,
        path: str,
        data: Any,
        *,
        filename: Optional[str] = None,
        field_name: str = "file",
        params: Optional[Dict[str, str]] = None,
        headers: Optional[Dict[str, str]] = None,
        on_progress: Optional[Callable[[int, int], None]] = None,
    ) -> Any:
        """Upload a file via multipart/form-data.

        Args:
            path: API path (e.g., "/api/files/upload").
            data: File path, bytes, or file-like object.
            filename: Explicit filename (auto-detected if not provided).
            field_name: Form field name (default: "file").
            params: Additional query params.
            headers: Additional headers.
            on_progress: Optional progress callback (bytes_sent, total_bytes).

        Returns:
            Parsed API response.
        """
        file_bytes, name, content_type = normalize_file_input(data, filename)
        files = {field_name: (name, file_bytes, content_type)}
        return self.request("POST", path, files=files, params=params, headers=headers)

    def download(
        self,
        path: str,
        destination: Optional[Union[str, Path]] = None,
        *,
        on_progress: Optional[Callable[[int, int], None]] = None,
    ) -> Union[bytes, str]:
        """Download a file, optionally saving directly to disk.

        When ``destination`` is provided, data is streamed directly to disk
        in 64 KiB chunks, keeping peak memory usage O(chunk_size) regardless
        of file size.

        Args:
            path: API path (e.g., "/api/files/download/{id}").
            destination: Local file path. If None, returns bytes in memory.
            on_progress: Optional callback(bytes_read, total_bytes).

        Returns:
            File content as bytes if destination is None, else the destination path.
        """
        resp = self.request(
            "GET", path, stream=True,
            on_download_progress=on_progress,
        )

        if destination is not None and not isinstance(resp, _StreamingResponse):
            dest_path = Path(destination)
            bytes_read = 0
            total = int(resp.headers.get("content-length", 0)) or None
            with open(dest_path, "wb") as f:
                for chunk in resp.iter_content(chunk_size=65536):
                    if chunk:
                        f.write(chunk)
                        bytes_read += len(chunk)
                        if on_progress:
                            on_progress(bytes_read, total or 0)
            return str(dest_path)

        if isinstance(resp, _StreamingResponse):
            if destination is not None:
                return resp.stream_to_file(Path(destination))
            return resp.read()

        if destination is not None:
            dest_path = Path(destination)
            dest_path.write_bytes(resp.content)
            return str(dest_path)

        return resp.content

    def close(self) -> None:
        """Close the HTTP session."""
        self._session.close()

    def _graphql(self, query: str, variables: Optional[Dict[str, Any]] = None) -> GraphQLResponse:
        """Execute a GraphQL query.

        Args:
            query: The GraphQL query string.
            variables: Optional query variables.

        Returns:
            A typed ``GraphQLResponse`` with ``.data`` and ``.errors``.

        Raises:
            GraphQLError: If the GraphQL response contains errors.
        """
        from cloudpool.exceptions import GraphQLError
        from cloudpool.models.graphql import GraphQLResponse

        payload: Dict[str, Any] = {"query": query}
        if variables:
            payload["variables"] = variables
        raw = self.request("POST", "/graphql", json=payload)
        raw_dict = raw if isinstance(raw, dict) else {}
        if raw_dict.get("errors"):
            errors = raw_dict["errors"]
            raise GraphQLError(
                errors[0].get("message", "GraphQL error") if errors else "Unknown GraphQL error",
                errors,
            )
        return GraphQLResponse(data=raw_dict.get("data", {}), errors=None)

    def _graphql_data(self, query: str, variables: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Execute a GraphQL query and return the top-level ``data`` dict.

        Args:
            query: The GraphQL query string.
            variables: Optional query variables.

        Returns:
            The ``data`` dict from the GraphQL response.
        """
        return self._graphql(query, variables).data


class _StreamingResponse:
    """Wrapper around a streaming HTTP response with progress tracking."""

    def __init__(
        self,
        response: requests.Response,
        total: int,
        on_progress: Callable[[int, int], None],
    ) -> None:
        self._response = response
        self._total = total
        self._on_progress = on_progress

    def read(self) -> bytes:
        """Read the entire streamed response with progress callbacks."""
        chunks: list[bytes] = []
        bytes_read = 0
        for chunk in self._response.iter_content(chunk_size=65536):
            if chunk:
                chunks.append(chunk)
                bytes_read += len(chunk)
                if self._on_progress:
                    self._on_progress(bytes_read, self._total)
        return b"".join(chunks)

    def stream_to_file(self, dest_path: Path) -> str:
        """Stream directly to a file, using on_progress callback per chunk."""
        bytes_read = 0
        with open(dest_path, "wb") as f:
            for chunk in self._response.iter_content(chunk_size=65536):
                if chunk:
                    f.write(chunk)
                    bytes_read += len(chunk)
                    if self._on_progress:
                        self._on_progress(bytes_read, self._total)
        return str(dest_path)

    def __getattr__(self, name: str) -> Any:
        """Delegate attribute access to the underlying response."""
        return getattr(self._response, name)


def _parse_retry_after(resp: requests.Response) -> int:
    """Parse the Retry-After header from a rate-limited response.

    Args:
        resp: The HTTP response.

    Returns:
        Seconds to wait before retrying (default 5).
    """
    retry_after = resp.headers.get("Retry-After", "")
    if retry_after.isdigit():
        return int(retry_after)
    return 5


def _extract_error_message(resp: requests.Response) -> str:
    """Extract a human-readable error message from an error response.

    Args:
        resp: The error HTTP response.

    Returns:
        Extracted error message string.
    """
    try:
        body = resp.json()
        if isinstance(body, dict):
            for key in ("error", "message", "detail", "error_description"):
                val = body.get(key)
                if isinstance(val, str) and val:
                    return val
                if isinstance(val, dict):
                    msg = val.get("message", "")
                    if msg:
                        return msg
    except (ValueError, TypeError):
        pass
    return resp.text[:500] if resp.text else resp.reason
