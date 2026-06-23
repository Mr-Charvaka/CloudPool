"""File storage service."""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Union

from cloudpool._client import CloudPoolClient
from cloudpool._utils import FileInput, normalize_file_input
from cloudpool.models.files import AuditLogEntry, Bucket, FileMetadata, FileShare


class FilesClient:
    """Synchronous file storage client.

    Handles file upload, download, sharing, bucket management, and
    storage quota. Supports progress callbacks for large transfers.

    Accessed via ``cloudpool.files`` on a ``CloudPool`` instance.
    """

    def __init__(self, client: CloudPoolClient) -> None:
        self._client = client

    def upload(
        self,
        file_path: FileInput,
        bucket: str = "default",
        filename: Optional[str] = None,
        content_type: Optional[str] = None,
        on_progress: Optional[Callable[[int, int], None]] = None,
    ) -> FileMetadata:
        """Upload a file.

        Args:
            file_path: File path (str or Path), bytes, or file-like object.
            bucket: Target bucket name.
            filename: Override the filename sent to the API.
            content_type: Override the MIME type.
            on_progress: Optional callback(bytes_sent, total_bytes).

        Returns:
            FileMetadata for the uploaded file.

        Examples:
            >>> cp.files.upload("photo.jpg")
            >>> cp.files.upload(b"data", bucket="backups", filename="backup.sql")
        """
        if isinstance(file_path, (str, Path)):
            path = Path(file_path)
            if on_progress and path.stat().st_size > 1024 * 1024:
                total = path.stat().st_size
                self._client.upload(
                    "/api/files/upload",
                    file_path,
                    filename=filename,
                    params={"bucket": bucket},
                    on_progress=on_progress,
                )
            else:
                data = self._client.upload(
                    "/api/files/upload",
                    file_path,
                    filename=filename,
                    params={"bucket": bucket},
                )
        else:
            file_bytes, name, _ = normalize_file_input(file_path, filename)
            data = self._client.upload(
                "/api/files/upload",
                file_bytes,
                filename=filename or name,
                params={"bucket": bucket},
            )
        if isinstance(data, dict):
            return FileMetadata.from_dict(data)
        return FileMetadata(id=str(data), name=filename or "unknown")

    def upload_from_bytes(
        self,
        data: bytes,
        filename: str,
        bucket: str = "default",
        content_type: Optional[str] = None,
    ) -> FileMetadata:
        """Upload a file from raw bytes.

        Args:
            data: File content as bytes.
            filename: Target filename.
            bucket: Target bucket name.
            content_type: MIME type (auto-detected if not provided).

        Returns:
            FileMetadata for the uploaded file.
        """
        return self.upload(
            data, bucket=bucket, filename=filename, content_type=content_type,
        )

    def list(
        self,
        page: int = 0,
        size: int = 20,
        bucket: Optional[str] = None,
    ) -> List[FileMetadata]:
        """List uploaded files.

        Args:
            page: Zero-indexed page number.
            size: Items per page.
            bucket: Filter by bucket name.

        Returns:
            List of FileMetadata objects.
        """
        params: Dict[str, Any] = {"page": page, "size": size}
        if bucket:
            params["bucket"] = bucket
        resp = self._client.request("GET", "/api/files", params=params)
        return [FileMetadata.from_dict(f) for f in (resp if isinstance(resp, list) else [])]

    def download(self, file_id: str) -> bytes:
        """Download a file to memory.

        Args:
            file_id: The file's unique identifier.

        Returns:
            File content as bytes.
        """
        return self._client.download(f"/api/files/download/{file_id}")

    def download_to_path(
        self,
        file_id: str,
        destination: Union[str, os.PathLike],
        on_progress: Optional[Callable[[int, int], None]] = None,
    ) -> str:
        """Download a file to disk.

        Args:
            file_id: The file's unique identifier.
            destination: Local file path.
            on_progress: Optional callback(bytes_read, total_bytes).

        Returns:
            The destination path string.
        """
        return self._client.download(
            f"/api/files/download/{file_id}",
            destination=destination,
            on_progress=on_progress,
        )

    def download_shared(self, token: str) -> bytes:
        """Download a file using a share token.

        Args:
            token: The share token from ``share()``.

        Returns:
            File content as bytes.
        """
        return self._client.download(f"/api/files/shared/{token}")

    def get_metadata(self, file_id: str) -> FileMetadata:
        """Get file metadata.

        Args:
            file_id: The file's unique identifier.

        Returns:
            FileMetadata object.
        """
        resp = self._client.request("GET", f"/api/files/{file_id}/metadata")
        if isinstance(resp, dict):
            return FileMetadata.from_dict(resp)
        return FileMetadata(id=file_id, name="", original_name="", size=0, mime_type="", extension="", bucket_name="", drive_location="", checksum="")

    def share(
        self,
        file_id: str,
        shared_with_email: Optional[str] = None,
        expiry_hours: Optional[int] = None,
    ) -> FileShare:
        """Create a share link for a file.

        Args:
            file_id: The file's unique identifier.
            shared_with_email: If set, only this email can access the share.
            expiry_hours: Share expiration in hours (default: 24).

        Returns:
            FileShare with the share token and details.
        """
        body: Dict[str, Any] = {}
        if shared_with_email is not None:
            body["sharedWithEmail"] = shared_with_email
        if expiry_hours is not None:
            body["expiryHours"] = expiry_hours
        resp = self._client.request(
            "POST", f"/api/files/{file_id}/share",
            json=body if body else None,
        )
        return FileShare.from_dict(resp)

    def list_buckets(self) -> List[Bucket]:
        """List all storage buckets.

        Returns:
            List of Bucket objects.
        """
        resp = self._client.request("GET", "/api/files/buckets")
        return [Bucket.from_dict(b) for b in (resp if isinstance(resp, list) else [])]

    def create_bucket(self, name: str, description: str = "") -> Bucket:
        """Create a new bucket via GraphQL.

        Args:
            name: Bucket name.
            description: Optional description.

        Returns:
            The created Bucket.
        """
        result = self._client._graphql_data(
            "mutation ($name: String!, $description: String) {"
            "  createBucket(name: $name, description: $description) { id name description }"
            "}",
            {"name": name, "description": description},
        )
        return Bucket.from_dict(result.get("createBucket", {}))

    def get_quota(self) -> Dict[str, int]:
        """Get storage quota information.

        Returns:
            Dict with 'limit' and 'usage' (bytes).
        """
        resp = self._client.request("GET", "/api/files/quota")
        if isinstance(resp, dict):
            return {
                "limit": resp.get("limit", 0),
                "usage": resp.get("usage", 0),
            }
        return {"limit": 0, "usage": 0}

    def get_logs(self) -> List[AuditLogEntry]:
        """Get file audit logs.

        Returns:
            List of AuditLogEntry objects.
        """
        resp = self._client.request("GET", "/api/files/logs")
        return [AuditLogEntry.from_dict(e) for e in (resp if isinstance(resp, list) else [])]

    def delete(self, file_id: str) -> None:
        """Delete a file.

        Args:
            file_id: The file's unique identifier.
        """
        self._client.request("DELETE", f"/api/files/{file_id}")


class AsyncFilesClient:
    """Asynchronous file storage client."""

    def __init__(self, client: Any) -> None:
        self._client = client

    async def upload(
        self,
        file_path: FileInput,
        bucket: str = "default",
        filename: Optional[str] = None,
        content_type: Optional[str] = None,
    ) -> FileMetadata:
        resp = await self._client.upload(
            "/api/files/upload",
            file_path,
            filename=filename,
            params={"bucket": bucket},
        )
        return FileMetadata.from_dict(resp)

    async def list(self, page: int = 0, size: int = 20) -> List[FileMetadata]:
        resp = await self._client.request("GET", "/api/files", params={"page": page, "size": size})
        return [FileMetadata.from_dict(f) for f in (resp if isinstance(resp, list) else [])]

    async def download(self, file_id: str) -> bytes:
        return await self._client.download(f"/api/files/download/{file_id}")

    async def download_to_path(self, file_id: str, destination: str) -> str:
        data = await self._client.download(f"/api/files/download/{file_id}")
        Path(destination).write_bytes(data)
        return destination

    async def get_metadata(self, file_id: str) -> FileMetadata:
        resp = await self._client.request("GET", f"/api/files/{file_id}/metadata")
        return FileMetadata.from_dict(resp)

    async def share(self, file_id: str, shared_with_email: Optional[str] = None, expiry_hours: Optional[int] = None) -> FileShare:
        body: Dict[str, Any] = {}
        if shared_with_email is not None:
            body["sharedWithEmail"] = shared_with_email
        if expiry_hours is not None:
            body["expiryHours"] = expiry_hours
        resp = await self._client.request("POST", f"/api/files/{file_id}/share", json=body if body else None)
        return FileShare.from_dict(resp)

    async def list_buckets(self) -> List[Bucket]:
        resp = await self._client.request("GET", "/api/files/buckets")
        return [Bucket.from_dict(b) for b in (resp if isinstance(resp, list) else [])]

    async def get_quota(self) -> Dict[str, int]:
        resp = await self._client.request("GET", "/api/files/quota")
        if isinstance(resp, dict):
            return {"limit": resp.get("limit", 0), "usage": resp.get("usage", 0)}
        return {"limit": 0, "usage": 0}

    async def get_logs(self) -> List[AuditLogEntry]:
        resp = await self._client.request("GET", "/api/files/logs")
        return [AuditLogEntry.from_dict(e) for e in (resp if isinstance(resp, list) else [])]

    async def delete(self, file_id: str) -> None:
        await self._client.request("DELETE", f"/api/files/{file_id}")
