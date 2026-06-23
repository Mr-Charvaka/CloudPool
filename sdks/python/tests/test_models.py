"""Tests for data models."""

from __future__ import annotations

from cloudpool.models.auth import ApiKey, ApiKeyAnalytics, AuthTokens, Project, User
from cloudpool.models.files import Bucket, FileMetadata, FileShare
from cloudpool.models.database import DevTable, FieldDefinition
from cloudpool.models.vector import VectorCollection
from cloudpool.models.kv import KvEntry
from cloudpool.models.emails import Email


class TestUserModel:
    def test_from_dict(self):
        d = {"id": "u1", "email": "a@b.com", "name": "Alice", "role": "admin",
             "storageQuota": 1000, "currentUsage": 100}
        u = User.from_dict(d)
        assert u.id == "u1"
        assert u.email == "a@b.com"
        assert u.role == "admin"


class TestAuthTokensModel:
    def test_from_dict(self):
        d = {"token": "t1", "refreshToken": "r1", "expiresIn": 3600}
        t = AuthTokens.from_dict(d)
        assert t.token == "t1"
        assert t.refresh_token == "r1"

    def test_from_dict_with_underscore_keys(self):
        d = {"token": "t1", "refresh_token": "r1"}
        t = AuthTokens.from_dict(d)
        assert t.refresh_token == "r1"


class TestFileMetadataModel:
    def test_from_dict(self):
        d = {
            "id": "f1",
            "name": "test.txt",
            "originalName": "test.txt",
            "size": 100,
            "mimeType": "text/plain",
            "extension": ".txt",
            "bucket": {"name": "default"},
            "driveLocation": "",
            "checksum": "abc",
        }
        f = FileMetadata.from_dict(d)
        assert f.id == "f1"
        assert f.bucket_name == "default"
        assert f.size == 100

    def test_from_dict_bucket_as_string(self):
        d = {"id": "f1", "name": "t.txt", "originalName": "t.txt", "size": 1,
             "mimeType": "", "extension": "", "bucket": "default", "driveLocation": "",
             "checksum": ""}
        f = FileMetadata.from_dict(d)
        assert f.bucket_name == "default"


class TestApiKeyModel:
    def test_from_dict(self):
        d = {"id": "ak1", "name": "dev key", "keyHash": "abc", "keyPrefix": "sk..."}
        k = ApiKey.from_dict(d)
        assert k.name == "dev key"
        assert k.is_active is True


class TestDevTableModel:
    def test_from_dict(self):
        d = {
            "id": "t1",
            "name": "users",
            "displayName": "Users",
            "fields": [
                {"fieldName": "name", "fieldType": "text"},
            ],
        }
        t = DevTable.from_dict(d)
        assert t.name == "users"
        assert len(t.fields) == 1
        assert t.fields[0].field_name == "name"

    def test_empty_fields(self):
        d = {"id": "t1", "name": "empty"}
        t = DevTable.from_dict(d)
        assert t.fields == []


class TestFieldDefinition:
    def test_to_dict(self):
        f = FieldDefinition("age", "number", required=True)
        d = f.to_dict()
        assert d["fieldName"] == "age"
        assert d["fieldType"] == "number"
        assert d["required"] is True

    def test_from_dict(self):
        d = {"fieldName": "email", "fieldType": "text", "required": True}
        f = FieldDefinition.from_dict(d)
        assert f.field_name == "email"
        assert f.required is True


class TestVectorCollectionModel:
    def test_from_dict(self):
        d = {"id": "vc1", "name": "docs", "dimension": 384, "distanceMetric": "cosine"}
        v = VectorCollection.from_dict(d)
        assert v.name == "docs"
        assert v.dimension == 384


class TestKvEntryModel:
    def test_from_dict(self):
        d = {"key": "mykey", "value": "myval", "ttlSeconds": 300}
        e = KvEntry.from_dict(d)
        assert e.key == "mykey"
        assert e.value == "myval"
        assert e.ttl_seconds == 300


class TestBucketModel:
    def test_from_dict(self):
        d = {"id": "b1", "name": "assets", "public": True}
        b = Bucket.from_dict(d)
        assert b.name == "assets"
        assert b.is_public is True


class TestFileShareModel:
    def test_from_dict(self):
        d = {"id": "s1", "fileId": "f1", "token": "tkn", "permission": "READ"}
        s = FileShare.from_dict(d)
        assert s.token == "tkn"
        assert s.permission == "READ"


class TestProjectModel:
    def test_from_dict(self):
        d = {"id": "p1", "name": "My Project"}
        p = Project.from_dict(d)
        assert p.id == "p1"
        assert p.name == "My Project"


class TestEmailModel:
    def test_from_dict(self):
        d = {"id": "e1", "to": "a@b.com", "subject": "Hello", "body": "World", "status": "sent"}
        e = Email.from_dict(d)
        assert e.to_addr == "a@b.com"
        assert e.status == "sent"
