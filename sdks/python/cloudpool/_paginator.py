"""Async and sync pagination utilities for list endpoints."""

from __future__ import annotations

from typing import Any, Callable, Dict, Iterator, List, Optional, TypeVar

T = TypeVar("T")


class Paginator:
    """Synchronous pagination helper.

    Iterates over paginated list endpoints, automatically fetching
    subsequent pages.

    Example:
        for item in Paginator(client.list_files, page_size=50):
            process(item)
    """

    def __init__(
        self,
        fetch_fn: Callable[..., List[T]],
        page_size: int = 20,
        max_pages: Optional[int] = None,
        **kwargs: Any,
    ) -> None:
        """Initialize paginator.

        Args:
            fetch_fn: A callable that accepts 'page' and 'size' params
                and returns a list of items.
            page_size: Number of items per page.
            max_pages: Maximum pages to fetch (None for unlimited).
            **kwargs: Additional keyword args forwarded to fetch_fn.
        """
        self._fetch_fn = fetch_fn
        self._page_size = page_size
        self._max_pages = max_pages
        self._kwargs = kwargs

    def __iter__(self) -> Iterator[T]:
        page = 0
        pages_fetched = 0
        while self._max_pages is None or pages_fetched < self._max_pages:
            items = self._fetch_fn(page=page, size=self._page_size, **self._kwargs)
            if not items:
                break
            yield from items
            page += 1
            pages_fetched += 1
            if len(items) < self._page_size:
                break

    def all(self) -> List[T]:
        """Fetch all items across all pages."""
        return list(self)


class AsyncPaginator:
    """Async pagination helper.

    Example:
        async for item in AsyncPaginator(client.list_files, page_size=50):
            process(item)
    """

    def __init__(
        self,
        fetch_fn: Callable[..., Any],
        page_size: int = 20,
        max_pages: Optional[int] = None,
        **kwargs: Any,
    ) -> None:
        self._fetch_fn = fetch_fn
        self._page_size = page_size
        self._max_pages = max_pages
        self._kwargs = kwargs

    def __aiter__(self) -> "AsyncPaginator":
        return self

    async def __anext__(self) -> T:
        if not hasattr(self, "_page"):
            self._page = 0
            self._pages_fetched = 0
            self._items: List[T] = []
            self._item_index = 0

        while self._item_index >= len(self._items):
            if self._max_pages is not None and self._pages_fetched >= self._max_pages:
                raise StopAsyncIteration

            items = await self._fetch_fn(
                page=self._page, size=self._page_size, **self._kwargs
            )
            if not items:
                raise StopAsyncIteration

            self._items = list(items)
            self._item_index = 0
            self._page += 1
            self._pages_fetched += 1

            if len(items) < self._page_size:
                # Last page
                pass

        item = self._items[self._item_index]
        self._item_index += 1
        return item

    async def all(self) -> List[T]:
        """Fetch all items across all pages."""
        result: List[T] = []
        async for item in self:
            result.append(item)
        return result
