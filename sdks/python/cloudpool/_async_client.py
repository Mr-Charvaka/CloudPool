"""Asynchronous HTTP client using aiohttp."""

from __future__ import annotations

import asyncio
import logging
from pathlib import Path
from typing import Any, Callable, Dict, Optional, TypeVar

from cloudpool._base_client import BaseCloudPoolClient
from cloudpool._config import CloudPoolConfig, ConfigLoader
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

T = TypeVar("T")


class AsyncCloudPoolClient(BaseCloudPoolClient):
    """Async HTTP client with aiohttp, retry, and connection pooling.

    Not instantiated directly — use ``AsyncCloudPool`` via
    ``CloudPool.async_client()``.
    """

    def __init__(
        self,
        config: CloudPoolConfig,
        credential_provider: Optional[CredentialChain] = None,
    ) -> None:
        super().__init__(config, credential_provider)
        self._session: Optional[aiohttp.ClientSession] = None
        self._timeout: float = float(config.timeout)

    async def _get_session(self) -> aiohttp.ClientSession:
        """Get or create the aiohttp session."""
        if self._session is None or self._session.closed:
            import aiohttp

            timeout = aiohttp.ClientTimeout(total=self._timeout)
            connector = aiohttp.TCPConnector(
                limit=25,
                limit_per_host=10,
                ttl_dns_cache=300,
            )
            self._session = aiohttp.ClientSession(
                timeout=timeout,
                connector=connector,
            )
        return self._session

    async def request(
        self,
        method: str,
        path: str,
        *,
        headers: Optional[Dict[str, str]] = None,
        params: Optional[Dict[str, Any]] = None,
        json: Any = None,
        data: Any = None,
        stream: bool = False,
        timeout: Optional[float] = None,
        max_retries: Optional[int] = None,
    ) -> Any:
        """Execute an async HTTP request with retry.

        Args:
            method: HTTP method.
            path: Request path.
            headers: Additional headers.
            params: Query string parameters.
            json: JSON body.
            data: Raw body.
            stream: If True, return the raw response for streaming.
            timeout: Override timeout.
            max_retries: Override max retries.

        Returns:
            Parsed response or raw aiohttp response (if stream=True).

        Raises:
            Same exception hierarchy as sync client.
        """
        import aiohttp

        session = await self._get_session()
        url = f"{self.config.base_url.rstrip('/')}/{path.lstrip('/')}"
        request_headers = self._build_headers(headers)

        retries = max_retries if max_retries is not None else self.config.max_retries

        last_exc: Optional[Exception] = None
        for attempt in range(retries + 1):
            try:
                async with session.request(
                    method=method,
                    url=url,
                    headers=request_headers,
                    params=params,
                    json=json,
                    data=data,
                    timeout=aiohttp.ClientTimeout(
                        total=timeout or self._timeout,
                    ),
                ) as resp:
                    if resp.status == 429 and attempt < retries:
                        retry_after = _parse_retry_after_async(resp)
                        logger.info(
                            "Rate limited (attempt %d/%d). Retrying after %ds.",
                            attempt + 1, retries + 1, retry_after,
                        )
                        await asyncio.sleep(retry_after)
                        continue

                    if stream:
                        return resp

                    return await self._handle_response_async(resp, method, path)

            except (asyncio.TimeoutError, aiohttp.ClientTimeout) as e:
                last_exc = ConnectionError(
                    f"Request timed out: {method} {path}",
                )
                if attempt < retries:
                    delay = self._retry_delay(attempt)
                    await asyncio.sleep(delay)
                    continue
                raise last_exc

            except aiohttp.ClientConnectorError as e:
                last_exc = ConnectionError(
                    f"Connection failed: {e}",
                )
                if attempt < retries:
                    delay = self._retry_delay(attempt)
                    await asyncio.sleep(delay)
                    continue
                raise last_exc

            except aiohttp.ClientError as e:
                raise CloudPoolError(f"HTTP request failed: {e}") from e

        if last_exc is not None:
            raise last_exc
        return None

    async def _handle_response_async(
        self,
        resp: aiohttp.ClientResponse,
        method: str,
        path: str,
    ) -> Any:
        """Process an async response."""
        status = resp.status
        text = await resp.text()

        if status == 401:
            raise AuthenticationError(
                "Invalid or expired credentials",
                status_code=status, body=text,
            )
        if status == 429:
            raise RateLimitError(
                "Rate limit exceeded",
                status_code=status, body=text,
                retry_after=_parse_retry_after_async(resp),
            )
        if status == 404:
            raise NotFoundError("Resource not found", status_code=status, body=text)
        if status == 409:
            raise ConflictError("Resource conflict", status_code=status, body=text)
        if status == 422:
            raise ValidationError("Validation failed", status_code=status, body=text)
        if status == 204:
            return None
        if status >= 400:
            raise CloudPoolError(
                f"API error [{status}]: {text[:500]}",
                status_code=status, body=text,
            )
        try:
            return await resp.json()
        except ValueError:
            return await resp.read()

    async def upload(
        self,
        path: str,
        data: Any,
        *,
        filename: Optional[str] = None,
        field_name: str = "file",
        params: Optional[Dict[str, str]] = None,
        headers: Optional[Dict[str, str]] = None,
    ) -> Any:
        """Upload a file via multipart/form-data (async).

        Args:
            path: API path.
            data: File path, bytes, or file-like.
            filename: Explicit filename.
            field_name: Form field name.
            params: Query params.
            headers: Additional headers.

        Returns:
            Parsed API response.
        """
        import aiohttp

        file_bytes, name, content_type = normalize_file_input(data, filename)
        form = aiohttp.FormData()
        form.add_field(
            field_name,
            file_bytes,
            filename=name,
            content_type=content_type,
        )
        return await self.request(
            "POST", path,
            data=form,
            params=params,
            headers=headers,
        )

    async def download(
        self,
        path: str,
        destination: Optional[Union[str, Path]] = None,
        *,
        on_progress: Optional[Callable[[int, int], None]] = None,
    ) -> Union[bytes, str]:
        """Download a file (async), streaming directly to disk when a destination is given.

        When ``destination`` is provided, data is streamed chunk-by-chunk to the file
        — peak memory usage stays O(chunk_size) regardless of file size.

        Args:
            path: API path.
            destination: Optional local path. If None, returns bytes.
            on_progress: Optional callback(bytes_read, total_bytes).

        Returns:
            File content as bytes if destination is None, else the destination path.
        """
        resp = await self.request("GET", path, stream=True)
        total = resp.content_length or 0
        bytes_read = 0

        if destination:
            dest_path = Path(destination)
            with open(dest_path, "wb") as f:
                async for chunk in resp.content.iter_chunked(65536):
                    if chunk:
                        f.write(chunk)
                        bytes_read += len(chunk)
                        if on_progress:
                            on_progress(bytes_read, total)
            return str(dest_path)

        chunks: list[bytes] = []
        async for chunk in resp.content.iter_chunked(65536):
            if chunk:
                chunks.append(chunk)
                bytes_read += len(chunk)
                if on_progress:
                    on_progress(bytes_read, total)
        return b"".join(chunks)

    async def _graphql(self, query: str, variables: Optional[Dict[str, Any]] = None) -> GraphQLResponse:
        """Execute a GraphQL query (async).

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
        raw = await self.request("POST", "/graphql", json=payload)
        raw_dict = raw if isinstance(raw, dict) else {}
        if raw_dict.get("errors"):
            errors = raw_dict["errors"]
            raise GraphQLError(
                errors[0].get("message", "GraphQL error") if errors else "Unknown GraphQL error",
                errors,
            )
        return GraphQLResponse(data=raw_dict.get("data", {}), errors=None)

    async def _graphql_data(self, query: str, variables: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Execute a GraphQL query and return the top-level ``data`` dict (async).

        Args:
            query: The GraphQL query string.
            variables: Optional query variables.

        Returns:
            The ``data`` dict from the GraphQL response.
        """
        resp = await self._graphql(query, variables)
        return resp.data

    async def close(self) -> None:
        """Close the aiohttp session."""
        if self._session and not self._session.closed:
            await self._session.close()

    async def __aenter__(self) -> AsyncCloudPoolClient:
        return self

    async def __aexit__(self, *exc: Any) -> None:
        await self.close()


class AsyncCloudPool:
    """Async CloudPool client with service sub-clients.

    Create via ``CloudPool.async_client()`` or directly:

        cp = AsyncCloudPool(api_key="...")
        result = await cp.files.list()
        await cp.close()
    """

    def __init__(
        self,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        jwt_token: Optional[str] = None,
        timeout: int = 30,
        max_retries: int = 3,
        config: Optional[CloudPoolConfig] = None,
        verbose: bool = False,
    ) -> None:
        if config is not None:
            cfg = config
        else:
            cfg = ConfigLoader().load(
                base_url=base_url,
                api_key=api_key,
                jwt_token=jwt_token,
                timeout=timeout,
                max_retries=max_retries,
                verbose=verbose,
            )
        self._config = cfg
        self._client = AsyncCloudPoolClient(cfg)
        self._init_services()

    def _init_services(self) -> None:
        from cloudpool.services.auth import AsyncAuthClient
        from cloudpool.services.files import AsyncFilesClient
        from cloudpool.services.database import AsyncDatabaseClient
        from cloudpool.services.vector import AsyncVectorClient
        from cloudpool.services.compute import AsyncComputeClient
        from cloudpool.services.network import AsyncNetworkClient
        from cloudpool.services.payments import AsyncPaymentsClient
        from cloudpool.services.kv import AsyncKvClient
        from cloudpool.services.emails import AsyncEmailsClient

        self.auth: AsyncAuthClient = AsyncAuthClient(self._client)
        self.files: AsyncFilesClient = AsyncFilesClient(self._client)
        self.database: AsyncDatabaseClient = AsyncDatabaseClient(self._client)
        self.vector: AsyncVectorClient = AsyncVectorClient(self._client)
        self.compute: AsyncComputeClient = AsyncComputeClient(self._client)
        self.network: AsyncNetworkClient = AsyncNetworkClient(self._client)
        self.payments: AsyncPaymentsClient = AsyncPaymentsClient(self._client)
        self.kv: AsyncKvClient = AsyncKvClient(self._client)
        self.emails: AsyncEmailsClient = AsyncEmailsClient(self._client)

    async def close(self) -> None:
        await self._client.close()

    async def __aenter__(self) -> AsyncCloudPool:
        return self

    async def __aexit__(self, *exc: Any) -> None:
        await self.close()

    def set_jwt_token(self, token: str) -> None:
        self._client.set_jwt_token(token)

    def set_api_key(self, key: str) -> None:
        self._client.set_api_key(key)


def _parse_retry_after_async(resp: Any) -> int:
    """Parse Retry-After from an aiohttp response."""
    retry_after = resp.headers.get("Retry-After", "")
    if retry_after.isdigit():
        return int(retry_after)
    return 5
