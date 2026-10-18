"""Log configuration for the local desktop service."""

from loguru import logger

from xianyu_client.core.constants import (
    LOCAL_LOG_FILE_NAME,
    LOG_ENCODING,
    LOG_RETENTION_DAYS,
    LOG_ROTATION_DAYS,
)


class SecretRedactionFilter:
    """Redacts configured secrets before records reach the log sink."""

    def __init__(self, secrets: tuple[str, ...]) -> None:
        """Create a filter for the secrets that must never be logged."""
        self._secrets = tuple(secret for secret in secrets if secret)

    def __call__(self, record: dict) -> bool:
        """Mutate a log record so secret values are replaced before output."""
        message = record["message"]
        for secret in self._secrets:
            message = message.replace(secret, "***")
        record["message"] = message
        return True


def setup_logging(log_dir, log_level: str, secrets: tuple[str, ...] = ()) -> None:
    """Configure file logging with daily rotation and secret redaction.

    Args:
        log_dir: Directory where local service logs are written.
        log_level: Minimum level accepted by the file sink.
        secrets: Values such as the local token that must be redacted.
    """
    logger.remove()
    logger.add(
        log_dir / LOCAL_LOG_FILE_NAME,
        level=log_level.upper(),
        rotation=f"{LOG_ROTATION_DAYS} day",
        retention=f"{LOG_RETENTION_DAYS} days",
        encoding=LOG_ENCODING,
        filter=SecretRedactionFilter(secrets),
        enqueue=True,
    )

