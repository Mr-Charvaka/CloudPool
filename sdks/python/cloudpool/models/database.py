"""Data models for the database service."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List


@dataclass
class FieldDefinition:
    """Schema definition for a table field.

    Attributes:
        field_name: The field name.
        field_type: The data type (e.g., "text", "number", "boolean").
        required: Whether this field is mandatory.
    """

    field_name: str
    field_type: str = "text"
    required: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return {
            "fieldName": self.field_name,
            "fieldType": self.field_type,
            "required": self.required,
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "FieldDefinition":
        return cls(
            field_name=d.get("fieldName", ""),
            field_type=d.get("fieldType", "text"),
            required=d.get("required", False),
        )


@dataclass
class DevTable:
    """A database table definition.

    Attributes:
        id: Unique table identifier.
        name: Table name.
        display_name: Human-readable table name.
        description: Table description.
        fields: List of field definitions.
        project_id: The owning project's ID.
        created_at: Creation timestamp.
    """

    id: str
    name: str
    display_name: str = ""
    description: str = ""
    fields: List[FieldDefinition] = field(default_factory=list)
    project_id: str = ""
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "DevTable":
        raw_fields = d.get("fields", [])
        fields = [
            FieldDefinition.from_dict(f) if isinstance(f, dict)
            else FieldDefinition(field_name=str(f), field_type="text")
            for f in raw_fields
        ]
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            display_name=d.get("displayName", ""),
            description=d.get("description", ""),
            fields=fields,
            project_id=d.get("projectId", ""),
            created_at=d.get("createdAt", ""),
        )
