"""Internal utility functions."""

from __future__ import annotations

import json
import mimetypes
import os
from pathlib import Path
from typing import Any, Dict, IO, Optional, Union

FileInput = Union[str, bytes, IO[Any], Path]


def normalize_file_input(
    data: FileInput,
    filename: Optional[str] = None,
) -> tuple[bytes, str, str]:
    """Convert various file input types to (bytes, filename, content_type).

    Args:
        data: A file path string, bytes, or a file-like object.
        filename: Explicit filename (used when data is bytes or IO).

    Returns:
        Tuple of (file_bytes, filename, content_type).
    """
    if isinstance(data, bytes):
        name = filename or "file.bin"
        file_bytes = data
    elif isinstance(data, (str, Path)):
        path = Path(data)
        name = filename or path.name
        file_bytes = path.read_bytes()
    elif isinstance(data, IO):
        file_bytes = data.read()
        name = filename or getattr(data, "name", "file.bin")
        if isinstance(name, Path):
            name = str(name)
    else:
        raise TypeError(f"Unsupported file input type: {type(data)}")

    if isinstance(name, Path):
        name = str(name)

    content_type, _ = mimetypes.guess_type(str(name))
    content_type = content_type or "application/octet-stream"

    return file_bytes, name, content_type


def build_user_agent() -> str:
    """Build a User-Agent string with SDK version info."""
    from cloudpool._version import __version__
    import platform
    import sys

    parts = [
        f"CloudPoolPythonSDK/{__version__}",
        f"{sys.implementation.name}/{sys.version.split()[0]}",
        f"({platform.system()} {platform.machine()})",
    ]
    return " ".join(parts)


def sanitize_headers(headers: Dict[str, Any]) -> Dict[str, str]:
    """Convert header values to strings, filtering out None."""
    return {
        k: str(v) if not isinstance(v, str) else v
        for k, v in headers.items()
        if v is not None
    }


def truncate(text: str, max_length: int = 80) -> str:
    """Truncate text with ellipsis if it exceeds max_length."""
    if len(text) <= max_length:
        return text
    return text[: max_length - 3] + "..."
