"""SQLAlchemy ORM models for local persistent data."""

from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Integer, String, Text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from xianyu_client.core.constants import (
    CONFIG_DESCRIPTION_MAX_LENGTH,
    CONFIG_KEY_MAX_LENGTH,
    CONFIG_TABLE_NAME,
    ACCOUNT_TABLE_NAME,
    ACCOUNT_GOODFISH_ID_MAX_LENGTH,
    ACCOUNT_GOODFISH_NICKNAME_MAX_LENGTH,
    ACCOUNT_INITIAL_IS_VALID,
    ACCOUNT_INITIAL_REMARK,
    ACCOUNT_LISTEN_STATE_IDLE,
    ACCOUNT_LISTEN_STATE_MAX_LENGTH,
    ACCOUNT_REMARK_MAX_LENGTH,
)


def utc_now() -> datetime:
    """Return the current timezone-aware UTC timestamp.

    Returns:
        Timezone-aware current UTC time.
    """
    return datetime.now(timezone.utc)


class Base(DeclarativeBase):
    """Base class for all local ORM models."""


class ConfigModel(Base):
    """Key-value configuration row stored in local SQLite."""

    __tablename__ = CONFIG_TABLE_NAME

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    key: Mapped[str] = mapped_column(String(CONFIG_KEY_MAX_LENGTH), unique=True, index=True)
    value: Mapped[str] = mapped_column(Text, nullable=False)
    description: Mapped[str] = mapped_column(
        "desc",
        String(CONFIG_DESCRIPTION_MAX_LENGTH),
        nullable=False,
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        onupdate=utc_now,
        nullable=False,
    )


class GoodfishAccountModel(Base):
    """Local Goodfish account whose cookie is stored as encrypted text."""

    __tablename__ = ACCOUNT_TABLE_NAME

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    goodfish_id: Mapped[str] = mapped_column(
        String(ACCOUNT_GOODFISH_ID_MAX_LENGTH),
        unique=True,
        index=True,
        nullable=False,
    )
    goodfish_nickname: Mapped[str] = mapped_column(
        String(ACCOUNT_GOODFISH_NICKNAME_MAX_LENGTH),
        nullable=False,
    )
    cookie: Mapped[str] = mapped_column(Text, nullable=False)
    remark: Mapped[str] = mapped_column(
        String(ACCOUNT_REMARK_MAX_LENGTH),
        default=ACCOUNT_INITIAL_REMARK,
        nullable=False,
    )
    is_valid: Mapped[bool] = mapped_column(
        Boolean,
        default=ACCOUNT_INITIAL_IS_VALID,
        nullable=False,
    )
    listen_state: Mapped[str] = mapped_column(
        String(ACCOUNT_LISTEN_STATE_MAX_LENGTH),
        default=ACCOUNT_LISTEN_STATE_IDLE,
        nullable=False,
    )
    last_check_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        onupdate=utc_now,
        nullable=False,
    )

    def is_listening_candidate(self) -> bool:
        """Return whether this account is currently allowed to enter listening.

        Returns:
            True only when listening is idle and the cookie is valid.
        """
        return self.listen_state == ACCOUNT_LISTEN_STATE_IDLE and self.is_valid

    def mark_cookie_validation(self, valid: bool, checked_at: datetime | None = None) -> None:
        """Update cookie validity while keeping timestamp logic inside the entity.

        Args:
            valid: Whether the latest cookie validation succeeded.
            checked_at: Optional validation timestamp; defaults to current UTC time.
        """
        self.is_valid = valid
        self.last_check_at = checked_at if checked_at is not None else utc_now()

