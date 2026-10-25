"""Log configuration for the local desktop service."""

import sys

from loguru import logger

from xianyu_client.core.constants import (
    LOCAL_LOG_FILE_NAME,
    LOG_BACKTRACE_ENABLED,
    LOG_COLORIZE_ENABLED,
    LOG_DIAGNOSE_ENABLED,
    LOG_ENCODING,
    LOG_FORMAT,
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
    """Configure console and file logging with daily rotation and secret redaction.

    The file sink writes UTF-8 without a BOM so log tooling can read it directly.
    The console sink writes to stderr, which Electron either mirrors to the
    development terminal or collects for startup diagnostics.

    Args:
        log_dir: Directory where local service logs are written.
        log_level: Minimum level accepted by both sinks.
        secrets: Values such as the local token that must be redacted.
    """
    logger.remove()
    level = log_level.upper()
    redaction = SecretRedactionFilter(secrets)
    logger.add(
        log_dir / LOCAL_LOG_FILE_NAME,
        level=level,
        format=LOG_FORMAT,
        rotation=f"{LOG_ROTATION_DAYS} day",
        retention=f"{LOG_RETENTION_DAYS} days",
        encoding=LOG_ENCODING,
        filter=redaction,
        enqueue=True,
        backtrace=LOG_BACKTRACE_ENABLED,
        diagnose=LOG_DIAGNOSE_ENABLED,
    )
    if sys.stderr is not None:
        logger.add(
            sys.stderr,
            level=level,
            format=LOG_FORMAT,
            filter=redaction,
            colorize=LOG_COLORIZE_ENABLED,
            backtrace=LOG_BACKTRACE_ENABLED,
            diagnose=LOG_DIAGNOSE_ENABLED,
        )
