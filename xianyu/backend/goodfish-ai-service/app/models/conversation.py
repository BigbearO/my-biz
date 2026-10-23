"""``ai_conversation``: one row per (item, buyer) conversation."""

from datetime import datetime

from sqlalchemy import DateTime, Index, String, desc, func
from sqlalchemy.orm import Mapped, mapped_column

from . import Base, BIGINT_PK_TYPE
from ..constants import (
    INDEX_CONVERSATION_LAST_MESSAGE_AT,
    INDEX_UK_CONVERSATION_ID,
    TABLE_AI_CONVERSATION,
)


class AiConversation(Base):
    __tablename__ = TABLE_AI_CONVERSATION
    __table_args__ = (
        Index(INDEX_UK_CONVERSATION_ID, "conversation_id", unique=True),
        # Restored together with the conversation list page: paginates by
        # last_message_at DESC, id DESC; without it the list triggers a full
        # table sort once the table grows.
        Index(
            INDEX_CONVERSATION_LAST_MESSAGE_AT,
            desc("last_message_at"),
            desc("id"),
        ),
    )

    id: Mapped[int] = mapped_column(BIGINT_PK_TYPE, primary_key=True, autoincrement=True)
    conversation_id: Mapped[str] = mapped_column(String(128), nullable=False)
    goodfish_item_id: Mapped[str] = mapped_column(String(64), nullable=False)
    buyer_id: Mapped[str] = mapped_column(String(64), nullable=False)
    goodfish_account_id: Mapped[str] = mapped_column(String(64), nullable=False)
    last_message_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
        onupdate=func.now(),
    )
