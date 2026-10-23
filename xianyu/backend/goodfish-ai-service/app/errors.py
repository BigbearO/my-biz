class ConfigEmptyError(RuntimeError):
    """Raised when neither local nor remote configuration is available."""


class ConfigFormatError(ValueError):
    """Raised when configuration content cannot be parsed."""


class ConfigNotFoundError(RuntimeError):
    """Raised when a configured Nacos data ID cannot be loaded."""


class NacosConnectionError(RuntimeError):
    """Raised when Nacos clients cannot be created after retries."""


class RegisterIpError(ValueError):
    """Raised when a usable service registration IP cannot be resolved."""


class OpenTelemetryConfigError(ValueError):
    """Raised when OpenTelemetry configuration is invalid."""


class ReplyBusinessError(Exception):
    """Base class for domain errors that map to a stable business code.

    Attributes:
        code: Business error code returned to the client (e.g. 40401).
        message: Stable user-facing message.
    """

    code: int
    message: str

    def __init__(self, code: int, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


class InvalidArgumentError(ReplyBusinessError):
    """Request payload failed validation (40001)."""

    def __init__(self, message: str) -> None:
        super().__init__(40001, message)


class ItemNotFoundError(ReplyBusinessError):
    """Item service reported the item does not exist (40401)."""

    def __init__(self, message: str) -> None:
        super().__init__(40401, message)


class ItemPromptEmptyError(ReplyBusinessError):
    """Item has no system prompt configured (40402); LLM is not called."""

    def __init__(self, message: str) -> None:
        super().__init__(40402, message)


class LlmCallFailedError(ReplyBusinessError):
    """LLM HTTP call failed or exceeded the time budget (50001)."""

    def __init__(self, message: str) -> None:
        super().__init__(50001, message)


class UnclassifiedServerError(ReplyBusinessError):
    """Downstream returned a status with no dedicated mapping (50001)."""

    def __init__(self, message: str) -> None:
        super().__init__(50001, message)


class NoEnabledModelError(ReplyBusinessError):
    """Model service reported no enabled model (50002); never retried."""

    def __init__(self, message: str) -> None:
        super().__init__(50002, message)


class UpstreamUnavailableError(ReplyBusinessError):
    """A downstream gRPC service is unavailable (50301)."""

    def __init__(self, message: str) -> None:
        super().__init__(50301, message)


class IdempotencyInProgressError(ReplyBusinessError):
    """The same request_id is currently being processed (50301)."""

    def __init__(self, message: str) -> None:
        super().__init__(50301, message)
