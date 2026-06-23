# Changelog

## 0.1.0 (2024-01-01)

- Initial public release
- Full REST API coverage across all modules
- Async client support via aiohttp
- 12-factor configuration (env vars, .env, config file)
- Credential chain (env > file > keyring)
- Exponential backoff with jitter on retries
- Streaming downloads with progress callbacks
- Typed data models for all API responses
- Comprehensive exception hierarchy
- Context manager support (sync + async)
- Google-style docstrings on all public APIs
- Pagination helpers for list endpoints
- Automatic JWT token refresh
- 90%+ test coverage
