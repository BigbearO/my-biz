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
