"""Data models for file storage."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Optional


@dataclass
class Bucket:
    """A storage bucket for organizing files.

    Attributes:
        id: Unique bucket identifier.
        name: Bucket name.
        description: Optional description.
        is_public: Whether bucket contents are publicly accessible.
        created_at: Creation timestamp.
        updated_at: Last update timestamp.
    """

    id: str
    name: str
    description: str = ""
    is_public: bool = False
    created_at: str = ""
    updated_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "Bucket":
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            description=d.get("description", ""),
            is_public=d.get("public", d.get("isPublic", False)),
            created_at=d.get("createdAt", ""),
            updated_at=d.get("updatedAt", ""),
        )


@dataclass
class FileMetadata:
    """Metadata for an uploaded file.

    Attributes:
        id: Unique file identifier.
        name: Storage name (may differ from original).
        original_name: Original uploaded filename.
        size: File size in bytes.
        mime_type: MIME type of the file.
        extension: File extension.
        bucket_name: Name of the containing bucket.
        drive_location: Google Drive path if synced.
        checksum: File checksum (SHA-256).
        is_public: Whether the file is publicly accessible.
        is_encrypted: Whether the file is encrypted at rest.
        created_at: Upload timestamp.
        updated_at: Last modification timestamp.
    """

    id: str
    name: str
    original_name: str = ""
    size: int = 0
    mime_type: str = ""
    extension: str = ""
    bucket_name: str = ""
    drive_location: str = ""
    checksum: str = ""
    is_public: bool = False
    is_encrypted: bool = False
    created_at: str = ""
    updated_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "FileMetadata":
        bucket = d.get("bucket", {})
        bucket_name = ""
        if isinstance(bucket, dict):
            bucket_name = bucket.get("name", "")
        elif isinstance(bucket, str):
            bucket_name = bucket
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            original_name=d.get("originalName", ""),
            size=d.get("size", 0),
            mime_type=d.get("mimeType", ""),
            extension=d.get("extension", ""),
            bucket_name=bucket_name,
            drive_location=d.get("driveLocation", ""),
            checksum=d.get("checksum", ""),
            is_public=d.get("public", d.get("isPublic", False)),
            is_encrypted=d.get("encrypted", d.get("isEncrypted", False)),
            created_at=d.get("createdAt", ""),
            updated_at=d.get("updatedAt", ""),
        )


@dataclass
class FileShare:
    """A file share link or invitation.

    Attributes:
        id: Unique share identifier.
        file_id: The shared file's ID.
        token: Share token for accessing the file.
        shared_with_email: Email of the share recipient, if any.
        permission: Access permission (e.g., "READ", "WRITE").
        expires_at: Share expiration timestamp.
        created_at: Share creation timestamp.
    """

    id: str
    file_id: str
    token: str = ""
    shared_with_email: Optional[str] = None
    permission: str = "READ"
    expires_at: Optional[str] = None
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "FileShare":
        return cls(
            id=d["id"],
            file_id=d.get("fileId", ""),
            token=d.get("token", ""),
            shared_with_email=d.get("sharedWithEmail"),
            permission=d.get("permission", "READ"),
            expires_at=d.get("expiresAt"),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class AuditLogEntry:
    """An audit log entry for file operations.

    Attributes:
        id: Unique log entry identifier.
        action: The action performed (e.g., "UPLOAD", "DELETE").
        entity_type: Type of entity affected.
        entity_id: ID of the affected entity.
        details: Human-readable description.
        timestamp: When the action occurred.
    """

    id: str
    action: str
    entity_type: str = ""
    entity_id: str = ""
    details: str = ""
    timestamp: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "AuditLogEntry":
        return cls(
            id=d["id"],
            action=d.get("action", ""),
            entity_type=d.get("entityType", ""),
            entity_id=d.get("entityId", ""),
            details=d.get("details", ""),
            timestamp=d.get("timestamp", ""),
        )
