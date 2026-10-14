class GrpcConfigError(ValueError):
    """Raised when gRPC discovery or channel configuration is invalid."""


class ServiceUnavailableError(RuntimeError):
    """Raised when a configured downstream has no usable gRPC channel."""


class InvalidUserContextError(ValueError):
    """Raised when a user-context value cannot be sent as gRPC metadata."""
