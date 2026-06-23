"""Authentication and account management service."""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from cloudpool._client import CloudPoolClient
from cloudpool.models.auth import (
    ApiKey,
    ApiKeyAnalytics,
    AuthTokens,
    DatabaseConnection,
    Project,
    Secret,
    Snapshot,
    User,
)


class AuthClient:
    """Synchronous authentication client.

    Handles user registration, login, token management, API keys,
    project management, and tenant authentication.

    Accessed via ``cloudpool.auth`` on a ``CloudPool`` instance.
    """

    def __init__(self, client: CloudPoolClient) -> None:
        self._client = client

    def register(self, email: str, password: str, name: str) -> AuthTokens:
        """Register a new account.

        Args:
            email: Account email address.
            password: Account password.
            name: Display name.

        Returns:
            AuthTokens containing JWT and refresh token.

        Raises:
            ValidationError: If the input data is invalid.
            ConflictError: If the email is already registered.
        """
        resp = self._client.request(
            "POST", "/api/auth/register",
            json={"email": email, "password": password, "name": name},
        )
        tokens = AuthTokens.from_dict(resp)
        self._client.set_jwt_token(tokens.token)
        return tokens

    def login(self, email: str, password: str) -> AuthTokens:
        """Log in with email and password.

        Automatically sets the JWT token on the client for subsequent requests.

        Args:
            email: Account email.
            password: Account password.

        Returns:
            AuthTokens containing JWT and refresh token.

        Raises:
            AuthenticationError: If credentials are invalid.
        """
        resp = self._client.request(
            "POST", "/api/auth/login",
            json={"email": email, "password": password},
        )
        tokens = AuthTokens.from_dict(resp)
        self._client.set_jwt_token(tokens.token)
        return tokens

    def me(self) -> User:
        """Get the current user's profile.

        Returns:
            User object with account details.
        """
        resp = self._client.request("GET", "/api/auth/me")
        return User.from_dict(resp)

    def refresh(self, refresh_token: str) -> AuthTokens:
        """Refresh the JWT token.

        Args:
            refresh_token: The refresh token obtained during login.

        Returns:
            New AuthTokens.
        """
        resp = self._client.request(
            "POST", "/api/auth/refresh",
            json={"refreshToken": refresh_token},
        )
        tokens = AuthTokens.from_dict(resp)
        self._client.set_jwt_token(tokens.token)
        return tokens

    def logout(self) -> None:
        """Log out and invalidate the current session."""
        self._client.request("POST", "/api/auth/logout")
        self._client.jwt_token = None

    def get_csrf_token(self) -> str:
        """Get a CSRF protection token.

        Returns:
            CSRF token string.
        """
        resp = self._client.request("GET", "/api/auth/csrf")
        if isinstance(resp, dict):
            return resp.get("token", "")
        return str(resp)

    def save_oauth_credentials(self, client_id: str, client_secret: str) -> Dict[str, Any]:
        """Save OAuth credentials for Google Drive integration.

        Args:
            client_id: OAuth client ID.
            client_secret: OAuth client secret.

        Returns:
            API response dict.
        """
        return self._client.request(
            "POST", "/api/auth/oauth-credentials",
            json={"clientId": client_id, "clientSecret": client_secret},
        )

    # ── Google Drive OAuth ──

    def get_google_auth_url(self) -> str:
        """Get the Google Drive OAuth authorization URL.

        Returns:
            The authorization URL string.
        """
        resp = self._client.request("GET", "/api/storage/google/auth-url")
        if isinstance(resp, dict):
            return resp.get("url", "")
        return str(resp)

    def get_google_drive_status(self) -> bool:
        """Check Google Drive linkage status.

        Returns:
            True if the account is linked to Google Drive.
        """
        resp = self._client.request("GET", "/api/storage/google/status")
        if isinstance(resp, dict):
            return resp.get("linked", False)
        return False

    # ── API Keys ──

    def list_api_keys(self) -> List[ApiKey]:
        """List all API keys for the account.

        Returns:
            List of ApiKey objects.
        """
        resp = self._client.request("GET", "/api/keys")
        return [ApiKey.from_dict(k) for k in (resp if isinstance(resp, list) else [])]

    def generate_api_key(
        self, name: str, description: str = "", days_to_live: int = 90,
    ) -> Dict[str, Any]:
        """Generate a new API key.

        Args:
            name: Human-readable key name.
            description: Optional description.
            days_to_live: Key validity period in days.

        Returns:
            Dict with 'id', 'name', 'key', and 'expiresAt' fields.
            The 'key' value is only shown once.
        """
        return self._client.request(
            "POST", "/api/keys/generate",
            json={"name": name, "description": description, "daysToLive": days_to_live},
        )

    def delete_api_key(self, key_id: str) -> None:
        """Delete an API key.

        Args:
            key_id: The key's unique identifier.
        """
        self._client.request("DELETE", f"/api/keys/{key_id}")

    def get_api_key_analytics(self) -> Dict[str, List[Any]]:
        """Get API key usage analytics.

        Returns:
            Dict with keys 'byKey', 'byStatus', 'byEndpoint', and 'logs'.
        """
        by_key = self._client.request("GET", "/api/keys/analytics/by-key")
        by_status = self._client.request("GET", "/api/keys/analytics/by-status")
        by_endpoint = self._client.request("GET", "/api/keys/analytics/by-endpoint")
        logs = self._client.request("GET", "/api/keys/analytics/logs")

        return {
            "by_key": [ApiKeyAnalytics.from_dict(k) for k in (by_key if isinstance(by_key, list) else [])],
            "by_status": by_status if isinstance(by_status, list) else [],
            "by_endpoint": by_endpoint if isinstance(by_endpoint, list) else [],
            "logs": logs if isinstance(logs, list) else [],
        }

    # ── Projects ──

    def list_projects(self) -> List[Project]:
        """List all projects.

        Returns:
            List of Project objects.
        """
        resp = self._client.request("GET", "/api/v1/projects")
        return [Project.from_dict(p) for p in (resp if isinstance(resp, list) else [])]

    def create_project(self, name: str, description: str = "") -> Project:
        """Create a new project.

        Args:
            name: Project name.
            description: Optional description.

        Returns:
            The created Project.
        """
        resp = self._client.request(
            "POST", "/api/v1/projects",
            json={"name": name, "description": description},
        )
        return Project.from_dict(resp)

    def delete_project(self, project_id: str) -> None:
        """Delete a project.

        Args:
            project_id: The project's unique identifier.
        """
        self._client.request("DELETE", f"/api/v1/projects/{project_id}")

    # ── Project Secrets ──

    def list_secrets(self, project_id: str) -> List[Secret]:
        """List secrets for a project.

        Args:
            project_id: The project's unique identifier.

        Returns:
            List of Secret objects.
        """
        resp = self._client.request("GET", f"/api/v1/projects/{project_id}/secrets")
        return [Secret.from_dict(s) for s in (resp if isinstance(resp, list) else [])]

    def add_secret(self, project_id: str, key: str, value: str) -> Secret:
        """Add a secret to a project.

        Args:
            project_id: The project's unique identifier.
            key: Secret key name.
            value: Secret value.

        Returns:
            The created Secret.
        """
        resp = self._client.request(
            "POST", f"/api/v1/projects/{project_id}/secrets",
            json={"key": key, "value": value},
        )
        return Secret.from_dict(resp)

    def delete_secret(self, secret_id: str) -> None:
        """Delete a secret.

        Args:
            secret_id: The secret's unique identifier.
        """
        self._client.request("DELETE", f"/api/v1/projects/secrets/{secret_id}")

    # ── Database Connections ──

    def list_connections(self, project_id: str) -> List[DatabaseConnection]:
        """List database connections for a project.

        Args:
            project_id: The project's unique identifier.

        Returns:
            List of DatabaseConnection objects.
        """
        resp = self._client.request("GET", f"/api/v1/projects/{project_id}/connections")
        return [DatabaseConnection.from_dict(c) for c in (resp if isinstance(resp, list) else [])]

    def save_connection(
        self,
        project_id: str,
        db_type: str,
        host: str,
        port: int,
        database_name: str,
        username: str,
        password: str,
        active: bool = True,
    ) -> DatabaseConnection:
        """Save a database connection.

        Args:
            project_id: The project's unique identifier.
            db_type: Database type (e.g., "postgres", "mysql").
            host: Database host.
            port: Database port.
            database_name: Database name.
            username: Database username.
            password: Database password.
            active: Whether the connection is active.

        Returns:
            The saved DatabaseConnection.
        """
        resp = self._client.request(
            "POST", f"/api/v1/projects/{project_id}/connections",
            json={
                "dbType": db_type,
                "host": host,
                "port": port,
                "databaseName": database_name,
                "username": username,
                "password": password,
                "active": active,
            },
        )
        return DatabaseConnection.from_dict(resp)

    def delete_connection(self, connection_id: str) -> None:
        """Delete a database connection.

        Args:
            connection_id: The connection's unique identifier.
        """
        self._client.request("DELETE", f"/api/v1/projects/connections/{connection_id}")

    def test_connection(
        self,
        project_id: str,
        db_type: str,
        host: str,
        port: int,
        database_name: str,
        username: str,
        password: str,
    ) -> bool:
        """Test a database connection.

        Args:
            project_id: The project's unique identifier.
            db_type: Database type.
            host: Database host.
            port: Database port.
            database_name: Database name.
            username: Database username.
            password: Database password.

        Returns:
            True if the connection is successful.
        """
        resp = self._client.request(
            "POST", f"/api/v1/projects/{project_id}/connections/test",
            json={
                "dbType": db_type,
                "host": host,
                "port": port,
                "databaseName": database_name,
                "username": username,
                "password": password,
            },
        )
        if isinstance(resp, dict):
            return resp.get("success", False)
        return False

    # ── Snapshots ──

    def list_snapshots(self, project_id: str) -> List[Snapshot]:
        """List snapshots for a project.

        Args:
            project_id: The project's unique identifier.

        Returns:
            List of Snapshot objects.
        """
        resp = self._client.request("GET", f"/api/v1/projects/{project_id}/snapshots")
        return [Snapshot.from_dict(s) for s in (resp if isinstance(resp, list) else [])]

    def create_snapshot(self, project_id: str, name: str) -> Snapshot:
        """Create a database snapshot.

        Args:
            project_id: The project's unique identifier.
            name: Snapshot name.

        Returns:
            The created Snapshot.
        """
        resp = self._client.request(
            "POST", f"/api/v1/projects/{project_id}/snapshots",
            json={"name": name},
        )
        return Snapshot.from_dict(resp)

    def restore_snapshot(self, project_id: str, snapshot_id: str) -> Dict[str, Any]:
        """Restore a database snapshot.

        Args:
            project_id: The project's unique identifier.
            snapshot_id: The snapshot's unique identifier.

        Returns:
            API response dict.
        """
        return self._client.request(
            "POST",
            f"/api/v1/projects/{project_id}/snapshots/{snapshot_id}/restore",
        )

    # ── Tenant Auth ──

    def tenant_list_users(self, project_id: str) -> List[User]:
        """List tenant users for a project.

        Args:
            project_id: The project's unique identifier.

        Returns:
            List of User objects.
        """
        resp = self._client.request("GET", f"/api/v1/projects/{project_id}/auth/users")
        return [User.from_dict(u) for u in (resp if isinstance(resp, list) else [])]

    def tenant_delete_user(self, project_id: str, user_id: str) -> None:
        """Delete a tenant user.

        Args:
            project_id: The project's unique identifier.
            user_id: The user's unique identifier.
        """
        self._client.request(
            "DELETE", f"/api/v1/projects/{project_id}/auth/users/{user_id}",
        )

    def tenant_signup(
        self,
        project_id: str,
        email: str,
        password: str,
        display_name: str,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> AuthTokens:
        """Register a new tenant user.

        Args:
            project_id: The project's unique identifier.
            email: User email.
            password: User password.
            display_name: User display name.
            metadata: Optional metadata dict.

        Returns:
            AuthTokens for the new user.
        """
        body: Dict[str, Any] = {
            "email": email,
            "password": password,
            "displayName": display_name,
        }
        if metadata:
            body["metadata"] = metadata
        resp = self._client.request(
            "POST", f"/api/v1/projects/{project_id}/auth/signup",
            json=body,
        )
        return AuthTokens.from_dict(resp)

    def tenant_login(self, project_id: str, email: str, password: str) -> AuthTokens:
        """Log in as a tenant user.

        Args:
            project_id: The project's unique identifier.
            email: User email.
            password: User password.

        Returns:
            AuthTokens for the tenant user.
        """
        resp = self._client.request(
            "POST", f"/api/v1/projects/{project_id}/auth/login",
            json={"email": email, "password": password},
        )
        return AuthTokens.from_dict(resp)

    def tenant_refresh(self, project_id: str, refresh_token: str) -> AuthTokens:
        """Refresh a tenant user's token.

        Args:
            project_id: The project's unique identifier.
            refresh_token: The tenant's refresh token.

        Returns:
            New AuthTokens.
        """
        resp = self._client.request(
            "POST", f"/api/v1/projects/{project_id}/auth/refresh",
            json={"refreshToken": refresh_token},
        )
        return AuthTokens.from_dict(resp)

    def tenant_logout(self, project_id: str, refresh_token: str) -> None:
        """Log out a tenant user.

        Args:
            project_id: The project's unique identifier.
            refresh_token: The tenant's refresh token.
        """
        self._client.request(
            "POST", f"/api/v1/projects/{project_id}/auth/logout",
            json={"refreshToken": refresh_token},
        )


class AsyncAuthClient:
    """Asynchronous authentication client.

    Same API as AuthClient, but all methods are async.
    Accessed via ``cloudpool.auth`` on an ``AsyncCloudPool`` instance.
    """

    def __init__(self, client: Any) -> None:
        self._client = client

    async def register(self, email: str, password: str, name: str) -> AuthTokens:
        resp = await self._client.request(
            "POST", "/api/auth/register",
            json={"email": email, "password": password, "name": name},
        )
        tokens = AuthTokens.from_dict(resp)
        self._client.set_jwt_token(tokens.token)
        return tokens

    async def login(self, email: str, password: str) -> AuthTokens:
        resp = await self._client.request(
            "POST", "/api/auth/login",
            json={"email": email, "password": password},
        )
        tokens = AuthTokens.from_dict(resp)
        self._client.set_jwt_token(tokens.token)
        return tokens

    async def me(self) -> User:
        resp = await self._client.request("GET", "/api/auth/me")
        return User.from_dict(resp)

    async def refresh(self, refresh_token: str) -> AuthTokens:
        resp = await self._client.request(
            "POST", "/api/auth/refresh",
            json={"refreshToken": refresh_token},
        )
        tokens = AuthTokens.from_dict(resp)
        self._client.set_jwt_token(tokens.token)
        return tokens

    async def logout(self) -> None:
        await self._client.request("POST", "/api/auth/logout")
        self._client.jwt_token = None

    async def list_api_keys(self) -> List[ApiKey]:
        resp = await self._client.request("GET", "/api/keys")
        return [ApiKey.from_dict(k) for k in (resp if isinstance(resp, list) else [])]

    async def generate_api_key(self, name: str, description: str = "", days_to_live: int = 90) -> Dict[str, Any]:
        return await self._client.request(
            "POST", "/api/keys/generate",
            json={"name": name, "description": description, "daysToLive": days_to_live},
        )

    async def delete_api_key(self, key_id: str) -> None:
        await self._client.request("DELETE", f"/api/keys/{key_id}")

    async def list_projects(self) -> List[Project]:
        resp = await self._client.request("GET", "/api/v1/projects")
        return [Project.from_dict(p) for p in (resp if isinstance(resp, list) else [])]

    async def create_project(self, name: str, description: str = "") -> Project:
        resp = await self._client.request(
            "POST", "/api/v1/projects",
            json={"name": name, "description": description},
        )
        return Project.from_dict(resp)

    async def delete_project(self, project_id: str) -> None:
        await self._client.request("DELETE", f"/api/v1/projects/{project_id}")
