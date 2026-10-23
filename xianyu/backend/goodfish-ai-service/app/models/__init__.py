"""ORM models for the conversation service (storage design sections 3.1-3.3)."""

from sqlalchemy import BigInteger, Integer
from sqlalchemy.orm import DeclarativeBase

# PostgreSQL uses BIGSERIAL; SQLite only auto-increments an INTEGER primary
# key, so the test database (aiosqlite) gets a variant that keeps inserts
# working without weakening the production schema.
BIGINT_PK_TYPE = BigInteger().with_variant(Integer, "sqlite")


class Base(DeclarativeBase):
    """Declarative base shared by the three conversation tables."""


# Imported after Base exists: the modules below import Base from this package.
from .conversation import AiConversation  # noqa: E402
from .message import AiConversationMessage  # noqa: E402
from .reply_log import AiReplyLog  # noqa: E402

__all__ = [
    "AiConversation",
    "AiConversationMessage",
    "AiReplyLog",
]
