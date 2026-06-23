"""Tests for the exception hierarchy."""

from __future__ import annotations

from cloudpool.exceptions import (
    AuthenticationError,
    CloudPoolError,
    ConflictError,
    ConnectionError,
    ConfigurationError,
    GraphQLError,
    NotFoundError,
    RateLimitError,
    ValidationError,
)


class TestExceptions:
    def test_cloudpool_error_base(self):
        err = CloudPoolError("something broke", status_code=500)
        assert err.message == "something broke"
        assert err.status_code == 500
        assert str(err) == "[500] something broke"

    def test_cloudpool_error_no_status(self):
        err = CloudPoolError("simple error")
        assert str(err) == "simple error"

    def test_authentication_error(self):
        err = AuthenticationError("bad creds", 401)
        assert isinstance(err, CloudPoolError)
        assert err.status_code == 401

    def test_rate_limit_error(self):
        err = RateLimitError("too fast", 429, retry_after=30)
        assert isinstance(err, CloudPoolError)
        assert err.retry_after == 30

    def test_not_found_error(self):
        err = NotFoundError("missing", 404)
        assert isinstance(err, CloudPoolError)
        assert err.status_code == 404

    def test_validation_error(self):
        err = ValidationError("invalid input", 422)
        assert isinstance(err, CloudPoolError)

    def test_conflict_error(self):
        err = ConflictError("duplicate", 409)
        assert isinstance(err, CloudPoolError)

    def test_graphql_error(self):
        errors = [{"message": "field X not found"}]
        err = GraphQLError("GraphQL error", errors)
        assert isinstance(err, CloudPoolError)
        assert err.errors == errors
        assert err.status_code == 400

    def test_configuration_error(self):
        err = ConfigurationError("bad config")
        assert isinstance(err, CloudPoolError)

    def test_connection_error(self):
        err = ConnectionError("network issue")
        assert isinstance(err, CloudPoolError)

    def test_exception_inheritance(self):
        assert issubclass(AuthenticationError, CloudPoolError)
        assert issubclass(RateLimitError, CloudPoolError)
        assert issubclass(NotFoundError, CloudPoolError)
        assert issubclass(ValidationError, CloudPoolError)
        assert issubclass(ConflictError, CloudPoolError)
        assert issubclass(GraphQLError, CloudPoolError)
        assert issubclass(ConfigurationError, CloudPoolError)
        assert issubclass(ConnectionError, CloudPoolError)
