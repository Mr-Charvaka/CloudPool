from __future__ import annotations

from typing import Any


class AuthClient:
    """Authentication APIs."""

    def __init__(self, cp):
        self.cp = cp

    def register(
        self,
        full_name: str,
        email: str,
        password: str,
    ) -> dict[str, Any]:

        result = self.cp.request(
            "POST",
            "/api/auth/register",
            json={
                "fullName": full_name,
                "email": email,
                "password": password,
            },
        )

        self.cp.set_token(result["token"])
        return result

    def login(
        self,
        email: str,
        password: str,
    ) -> dict[str, Any]:

        result = self.cp.request(
            "POST",
            "/api/auth/login",
            json={
                "email": email,
                "password": password,
            },
        )

        self.cp.set_token(result["token"])
        return result

    def me(self) -> dict[str, Any]:
        return self.cp.request(
            "GET",
            "/api/auth/me",
        )