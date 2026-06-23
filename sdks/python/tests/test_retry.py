"""Tests for retry logic with exponential backoff."""

from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest

from cloudpool._retry import exponential_backoff, retry


class TestExponentialBackoff:
    def test_backoff_increases_with_attempts(self):
        delays = [exponential_backoff(i, base_delay=1, max_delay=10, jitter=False)
                  for i in range(5)]
        for i in range(1, len(delays)):
            assert delays[i] >= delays[i - 1]

    def test_backoff_capped_at_max(self):
        delay = exponential_backoff(100, base_delay=1, max_delay=10, jitter=False)
        assert delay <= 10

    def test_backoff_with_jitter(self, monkeypatch):
        monkeypatch.setattr("random.uniform", lambda lo, hi: lo)
        delay = exponential_backoff(2, base_delay=1, max_delay=10, jitter=True)
        assert 0 <= delay <= 4  # base * 2^2 = 4

    def test_backoff_base_delay(self):
        delay = exponential_backoff(0, base_delay=0.5, max_delay=30, jitter=False)
        assert delay == 0.5


class TestRetry:
    def test_retry_succeeds_first_attempt(self):
        fn = MagicMock(return_value="success")
        result = retry(fn, max_retries=3)
        assert result == "success"
        fn.assert_called_once()

    def test_retry_succeeds_after_failures(self):
        fn = MagicMock(side_effect=[ValueError("fail"), ValueError("fail"), "success"])
        result = retry(fn, max_retries=3)
        assert result == "success"
        assert fn.call_count == 3

    def test_retry_exhausted_raises(self):
        fn = MagicMock(side_effect=ValueError("always fails"))
        with pytest.raises(ValueError):
            retry(fn, max_retries=2)
        assert fn.call_count == 3  # initial + 2 retries

    def test_retry_with_zero_retries(self):
        fn = MagicMock(side_effect=ValueError("fail"))
        with pytest.raises(ValueError):
            retry(fn, max_retries=0)
        fn.assert_called_once()

    def test_retry_calls_on_retry_callback(self):
        fn = MagicMock(side_effect=[ValueError("fail"), "success"])
        callback = MagicMock()
        retry(fn, max_retries=1, on_retry=callback)
        callback.assert_called_once()

    def test_retry_non_retryable_exception_raises_immediately(self):
        fn = MagicMock(side_effect=TypeError("not retryable"))
        with pytest.raises(TypeError):
            retry(fn, max_retries=3, retryable_exceptions=(ValueError,))
        fn.assert_called_once()
