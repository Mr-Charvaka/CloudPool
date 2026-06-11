from __future__ import annotations

from typing import Any


class DatabaseClient:
    """Database APIs."""

    def __init__(self, cp):
        self.cp = cp

    def list_tables(
        self,
        project_id: str,
    ) -> list[dict[str, Any]]:
        return self.cp.request(
            "GET",
            f"/api/database/tables?projectId={project_id}",
        )

    def create_table(
        self,
        project_id: str,
        name: str,
        display_name: str,
        fields: list[dict[str, Any]],
    ) -> dict[str, Any]:
        return self.cp.request(
            "POST",
            "/api/database/tables",
            json={
                "projectId": project_id,
                "name": name,
                "displayName": display_name,
                "fields": fields,
            },
        )

    def delete_table(
        self,
        table_id: str,
    ) -> None:
        self.cp.request(
            "DELETE",
            f"/api/database/tables/{table_id}",
        )

    def query(
        self,
        sql: str,
        connection_id: str | None = None,
    ) -> dict[str, Any]:
        return self.cp.request(
            "POST",
            "/api/console/query",
            json={
                "sql": sql,
                "connectionId": connection_id,
            },
        )

    def insert(
        self,
        table_id: str,
        data: dict[str, Any],
    ) -> dict[str, Any]:
        return self.cp.request(
            "POST",
            f"/api/database/tables/{table_id}/rows",
            json=data,
        )

    def get_rows(
        self,
        table_id: str,
    ) -> list[dict[str, Any]]:
        return self.cp.request(
            "GET",
            f"/api/database/tables/{table_id}/rows",
        )

    def delete_row(
        self,
        table_id: str,
        row_id: str,
    ) -> None:
        self.cp.request(
            "DELETE",
            f"/api/database/tables/{table_id}/rows/{row_id}",
        )