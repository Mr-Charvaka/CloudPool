from __future__ import annotations

from typing import Any


class StorageClient:
    """Storage APIs."""

    def __init__(self, cp):
        self.cp = cp

    def list_buckets(self) -> list[dict[str, Any]]:
        return self.cp.request(
            "GET",
            "/api/files/buckets",
        )

    def create_bucket(self, name: str) -> dict[str, Any]:
        return self.cp.request(
            "POST",
            "/api/files/buckets",
            json={"name": name},
        )

    def upload(
        self,
        bucket_name: str,
        file_obj,
        file_name: str,
    ) -> dict[str, Any]:

        files = {
            "file": (file_name, file_obj)
        }

        data = {
            "bucketName": bucket_name
        }

        return self.cp.request(
            "POST",
            "/api/files/upload",
            data=data,
            files=files,
        )

    def list_files(
        self,
        bucket_name: str,
    ) -> list[dict[str, Any]]:
        return self.cp.request(
            "GET",
            f"/api/files?bucketName={bucket_name}",
        )

    def delete_file(
        self,
        file_id: str,
    ) -> None:
        self.cp.request(
            "DELETE",
            f"/api/files/{file_id}",
        )

    def share(
        self,
        file_id: str,
        expires_in_hours: int = 24,
    ) -> dict[str, Any]:
        return self.cp.request(
            "POST",
            f"/api/files/{file_id}/share",
            json={
                "expiresInHours": expires_in_hours
            },
        )

    def search(
        self,
        query: str,
    ) -> list[dict[str, Any]]:
        return self.cp.request(
            "GET",
            f"/api/vector/search?query={query}",
        )