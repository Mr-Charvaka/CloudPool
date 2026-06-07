from __future__ import annotations

from typing import Any


class ProjectClient:
    """Project management APIs."""

    def __init__(self, cp):
        self.cp = cp

    def list(self) -> list[dict[str, Any]]:
        """List all projects."""
        return self.cp.request("GET", "/api/projects")

    def create(
        self,
        name: str,
        description: str | None = None,
    ) -> dict[str, Any]:
        """Create a project."""
        return self.cp.request(
            "POST",
            "/api/projects",
            json={
                "name": name,
                "description": description,
            },
        )

    def get(self, project_id: str) -> dict[str, Any]:
        """Get project by ID."""
        return self.cp.request(
            "GET",
            f"/api/projects/{project_id}",
        )

    def delete(self, project_id: str) -> None:
        """Delete project."""
        self.cp.request(
            "DELETE",
            f"/api/projects/{project_id}",
        )

    def snapshot(
        self,
        project_id: str,
        label: str | None = None,
    ) -> dict[str, Any]:
        """Create snapshot."""
        return self.cp.request(
            "POST",
            f"/api/projects/{project_id}/snapshot",
            json={"label": label},
        )

    def restore(
        self,
        project_id: str,
        snapshot_id: str,
    ) -> None:
        """Restore snapshot."""
        self.cp.request(
            "POST",
            f"/api/projects/{project_id}/restore/{snapshot_id}",
        )