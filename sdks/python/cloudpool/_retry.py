"""Exponential backoff with jitter for retrying failed requests.

Implements "full jitter" strategy as recommended by AWS:
    sleep = random_between(0, min(cap, base * 2^attempt))
"""

from __future__ import annotations

import random
import time
from typing import Callable, Optional, TypeVar

import requests

T = TypeVar("T")


def exponential_backoff(
    attempt: int,
    base_delay: float = 0.5,
    max_delay: float = 30.0,
    jitter: bool = True,
) -> float:
    """Calculate sleep duration with exponential backoff and jitter.

    Args:
        attempt: Zero-indexed attempt number.
        base_delay: Base delay in seconds.
        max_delay: Maximum delay cap in seconds.
        jitter: If True, apply full jitter randomization.

    Returns:
        Delay in seconds to sleep before next retry.
    """
    delay = min(max_delay, base_delay * (2 ** attempt))
    if jitter:
        delay = random.uniform(0, delay)
    return delay


_DEFAULT_RETRYABLE: tuple = (
    requests.exceptions.ConnectionError,
    requests.exceptions.Timeout,
    requests.exceptions.ReadTimeout,
    requests.exceptions.ConnectTimeout,
)


def retry(
    fn: Callable[[], T],
    max_retries: int = 3,
    base_delay: float = 0.5,
    max_delay: float = 30.0,
    retryable_exceptions: tuple = _DEFAULT_RETRYABLE,
    on_retry: Optional[Callable[[int, Exception], None]] = None,
) -> T:
    """Execute a callable with retry on failure.

    Args:
        fn: The callable to execute.
        max_retries: Maximum number of retry attempts (0 = no retry).
        base_delay: Base delay for exponential backoff.
        max_delay: Maximum delay cap.
        retryable_exceptions: Tuple of exception types that trigger a retry.
            If empty (``()``), all non-retryable exceptions propagate
            immediately — only ``(ConnectionError, Timeout, ...)`` are
            retried by default.
        on_retry: Optional callback invoked before each retry with
            (attempt, exception).

    Returns:
        The return value of fn.

    Raises:
        The last exception raised by fn if all retries are exhausted.
        Non-retryable exceptions are raised immediately.
    """
    last_exc: Optional[Exception] = None
    for attempt in range(max_retries + 1):
        try:
            return fn()
        except retryable_exceptions as e:
            last_exc = e
            if attempt >= max_retries:
                raise
            delay = exponential_backoff(attempt, base_delay, max_delay)
            if on_retry:
                on_retry(attempt + 1, e)
            time.sleep(delay)
        except Exception as e:
            raise

    if last_exc is not None:
        raise last_exc
    raise RuntimeError("Retry exhausted without exception")
