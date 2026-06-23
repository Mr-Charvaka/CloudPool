"""Database service client."""

from __future__ import annotations

from typing import Any, Dict, List, Optional, Union

from cloudpool._client import CloudPoolClient
from cloudpool.models.database import DevTable, FieldDefinition


class DatabaseClient:
    """Synchronous database client.

    Manages tables, records, and direct SQL queries.
    Supports both REST and GraphQL operations.

    Accessed via ``cloudpool.database`` on a ``CloudPool`` instance.
    """

    def __init__(self, client: CloudPoolClient) -> None:
        self._client = client

    def create_table(
        self,
        name: str,
        display_name: str,
        description: str,
        fields: List[Union[FieldDefinition, Dict[str, Any]]],
        project_id: Optional[str] = None,
    ) -> DevTable:
        """Create a new database table.

        Args:
            name: Table name (lowercase, no spaces).
            display_name: Human-readable table name.
            description: Table description.
            fields: List of FieldDefinition objects or field dicts.
            project_id: Optional project ID to scope the table.

        Returns:
            The created DevTable.
        """
        serialized = [
            f.to_dict() if isinstance(f, FieldDefinition) else f
            for f in fields
        ]
        body: Dict[str, Any] = {
            "name": name,
            "displayName": display_name,
            "description": description,
            "fields": serialized,
        }
        if project_id:
            body["projectId"] = project_id
        resp = self._client.request("POST", "/api/v1/db/tables", json=body)
        return DevTable.from_dict(resp)

    def list_tables(self, project_id: Optional[str] = None) -> List[DevTable]:
        """List all database tables.

        Args:
            project_id: Optional filter by project.

        Returns:
            List of DevTable objects.
        """
        params = {}
        if project_id:
            params["projectId"] = project_id
        resp = self._client.request("GET", "/api/v1/db/tables", params=params)
        return [DevTable.from_dict(t) for t in (resp if isinstance(resp, list) else [])]

    def get_table(self, table_id: str) -> DevTable:
        """Get a table by ID.

        Args:
            table_id: The table's unique identifier.

        Returns:
            The DevTable object.
        """
        resp = self._client.request("GET", f"/api/v1/db/tables/{table_id}")
        return DevTable.from_dict(resp)

    def get_table_fields(self, table_id: str) -> List[FieldDefinition]:
        """Get field definitions for a table.

        Args:
            table_id: The table's unique identifier.

        Returns:
            List of FieldDefinition objects.
        """
        resp = self._client.request("GET", f"/api/v1/db/tables/{table_id}/fields")
        return [FieldDefinition.from_dict(f) for f in (resp if isinstance(resp, list) else [])]

    def delete_table(self, table_id: str) -> None:
        """Delete a table.

        Args:
            table_id: The table's unique identifier.
        """
        self._client.request("DELETE", f"/api/v1/db/tables/{table_id}")

    def insert_record(self, table_id: str, record: Dict[str, Any]) -> Dict[str, Any]:
        """Insert a record into a table.

        Args:
            table_id: The table's unique identifier.
            record: Dict of field_name -> value.

        Returns:
            The created record with its assigned ID.
        """
        return self._client.request(
            "POST", f"/api/v1/db/tables/{table_id}/records",
            json=record,
        )

    def query_records(self, table_id: str) -> List[Dict[str, Any]]:
        """Query all records from a table.

        Args:
            table_id: The table's unique identifier.

        Returns:
            List of record dicts.
        """
        resp = self._client.request("GET", f"/api/v1/db/tables/{table_id}/records")
        return resp if isinstance(resp, list) else []

    def delete_record(self, table_id: str, record_id: str) -> None:
        """Delete a record from a table.

        Args:
            table_id: The table's unique identifier.
            record_id: The record's unique identifier.
        """
        self._client.request("DELETE", f"/api/v1/db/tables/{table_id}/records/{record_id}")

    def query(self, sql: str, database_connection_id: Optional[str] = None) -> Dict[str, Any]:
        """Execute a raw SQL query against a database connection.

        Args:
            sql: The SQL query string.
            database_connection_id: ID of the database connection to use.

        Returns:
            Query results as a dict.
        """
        body: Dict[str, Any] = {"sql": sql}
        if database_connection_id:
            body["connectionId"] = database_connection_id
        return self._client.request("POST", "/api/v1/db/query", json=body)

    # ── GraphQL Operations ──

    def create_table_graphql(
        self,
        name: str,
        display_name: str,
        description: str,
        fields: List[Union[FieldDefinition, Dict[str, Any]]],
    ) -> DevTable:
        """Create a table via GraphQL.

        Args:
            name: Table name.
            display_name: Human-readable name.
            description: Table description.
            fields: List of field definitions.

        Returns:
            The created DevTable.
        """
        serialized = [
            f.to_dict() if isinstance(f, FieldDefinition) else f
            for f in fields
        ]
        result = self._client._graphql_data(
            "mutation CreateTable($name: String!, $displayName: String, "
            "$description: String, $fields: [FieldInput!]) {"
            "  createTable(name: $name, displayName: $displayName, "
            "    description: $description, fields: $fields) {"
            "    id name displayName description"
            "  }"
            "}",
            {
                "name": name,
                "displayName": display_name,
                "description": description,
                "fields": serialized,
            },
        )
        return DevTable.from_dict(result.get("createTable", {}))

    def list_tables_graphql(self) -> List[DevTable]:
        """List tables via GraphQL.

        Returns:
            List of DevTable objects.
        """
        result = self._client._graphql_data(
            "query { tables { id name displayName description } }"
        )
        return [DevTable.from_dict(t) for t in result.get("tables", [])]

    def insert_record_graphql(self, table_id: str, data: Dict[str, Any]) -> Dict[str, Any]:
        """Insert a record via GraphQL.

        Args:
            table_id: The table's unique identifier.
            data: Dict of field_name -> value.

        Returns:
            The created record.
        """
        entries = [{"key": k, "value": str(v)} for k, v in data.items()]
        result = self._client._graphql_data(
            "mutation InsertRecord($tableId: UUID!, $data: [KeyValueInput!]) {"
            "  insertRecord(tableId: $tableId, data: $data) { id }"
            "}",
            {"tableId": table_id, "data": entries},
        )
        return result.get("insertRecord", {})

    def query_records_graphql(self, table_id: str) -> List[Dict[str, Any]]:
        """Query records via GraphQL.

        Args:
            table_id: The table's unique identifier.

        Returns:
            List of record dicts.
        """
        result = self._client._graphql_data(
            "query Records($tableId: UUID!) { records(tableId: $tableId) }",
            {"tableId": table_id},
        )
        return result.get("records", [])


class AsyncDatabaseClient:
    """Asynchronous database client."""

    def __init__(self, client: Any) -> None:
        self._client = client

    async def create_table(
        self,
        name: str,
        display_name: str,
        description: str,
        fields: List[Union[FieldDefinition, Dict[str, Any]]],
        project_id: Optional[str] = None,
    ) -> DevTable:
        serialized = [f.to_dict() if isinstance(f, FieldDefinition) else f for f in fields]
        body: Dict[str, Any] = {"name": name, "displayName": display_name, "description": description, "fields": serialized}
        if project_id:
            body["projectId"] = project_id
        resp = await self._client.request("POST", "/api/v1/db/tables", json=body)
        return DevTable.from_dict(resp)

    async def list_tables(self, project_id: Optional[str] = None) -> List[DevTable]:
        params = {}
        if project_id:
            params["projectId"] = project_id
        resp = await self._client.request("GET", "/api/v1/db/tables", params=params)
        return [DevTable.from_dict(t) for t in (resp if isinstance(resp, list) else [])]

    async def get_table(self, table_id: str) -> DevTable:
        resp = await self._client.request("GET", f"/api/v1/db/tables/{table_id}")
        return DevTable.from_dict(resp)

    async def insert_record(self, table_id: str, record: Dict[str, Any]) -> Dict[str, Any]:
        return await self._client.request("POST", f"/api/v1/db/tables/{table_id}/records", json=record)

    async def query_records(self, table_id: str) -> List[Dict[str, Any]]:
        resp = await self._client.request("GET", f"/api/v1/db/tables/{table_id}/records")
        return resp if isinstance(resp, list) else []

    async def delete_record(self, table_id: str, record_id: str) -> None:
        await self._client.request("DELETE", f"/api/v1/db/tables/{table_id}/records/{record_id}")

    async def delete_table(self, table_id: str) -> None:
        await self._client.request("DELETE", f"/api/v1/db/tables/{table_id}")
