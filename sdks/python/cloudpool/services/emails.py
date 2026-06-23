"""Email service client."""

from __future__ import annotations

from typing import Any, Dict, List

from cloudpool._client import CloudPoolClient
from cloudpool.models.emails import Email


class EmailsClient:
    """Synchronous email client.

    Send emails and view inbox. Each CloudPool account includes
    a built-in email service.

    Accessed via ``cloudpool.emails`` on a ``CloudPool`` instance.
    """

    def __init__(self, client: CloudPoolClient) -> None:
        self._client = client

    def list_sent(self) -> List[Email]:
        """List all sent emails.

        Returns:
            List of Email objects.
        """
        resp = self._client.request("GET", "/api/dev/emails")
        return [Email.from_dict(e) for e in (resp if isinstance(resp, list) else [])]

    def clear_sent(self) -> None:
        """Clear all sent email records."""
        self._client.request("DELETE", "/api/dev/emails")

    def list_inbox(self) -> List[Email]:
        """List received emails (inbox).

        Returns:
            List of Email objects.
        """
        resp = self._client.request("GET", "/api/dev/emails/inbox")
        return [Email.from_dict(e) for e in (resp if isinstance(resp, list) else [])]

    def clear_inbox(self) -> None:
        """Clear the inbox."""
        self._client.request("DELETE", "/api/dev/emails/inbox")

    def send_test(self, to: str, subject: str, body: str) -> Dict[str, Any]:
        """Send a test email.

        Args:
            to: Recipient email address.
            subject: Email subject.
            body: Email body text.

        Returns:
            API response with status.
        """
        return self._client.request(
            "POST", "/api/dev/emails/send-test",
            json={"to": to, "subject": subject, "body": body},
        )

    def send_direct(self, to: str, subject: str, body: str) -> Dict[str, Any]:
        """Send a direct email.

        Args:
            to: Recipient email address.
            subject: Email subject.
            body: Email body text.

        Returns:
            API response with status.
        """
        return self._client.request(
            "POST", "/api/dev/emails/send-direct",
            json={"to": to, "subject": subject, "body": body},
        )


class AsyncEmailsClient:
    """Asynchronous email client."""

    def __init__(self, client: Any) -> None:
        self._client = client

    async def list_sent(self) -> List[Email]:
        resp = await self._client.request("GET", "/api/dev/emails")
        return [Email.from_dict(e) for e in (resp if isinstance(resp, list) else [])]

    async def clear_sent(self) -> None:
        await self._client.request("DELETE", "/api/dev/emails")

    async def list_inbox(self) -> List[Email]:
        resp = await self._client.request("GET", "/api/dev/emails/inbox")
        return [Email.from_dict(e) for e in (resp if isinstance(resp, list) else [])]

    async def clear_inbox(self) -> None:
        await self._client.request("DELETE", "/api/dev/emails/inbox")

    async def send_test(self, to: str, subject: str, body: str) -> Dict[str, Any]:
        return await self._client.request("POST", "/api/dev/emails/send-test", json={"to": to, "subject": subject, "body": body})

    async def send_direct(self, to: str, subject: str, body: str) -> Dict[str, Any]:
        return await self._client.request("POST", "/api/dev/emails/send-direct", json={"to": to, "subject": subject, "body": body})
