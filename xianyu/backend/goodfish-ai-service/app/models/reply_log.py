"""``ai_reply_log``: reply decision log for troubleshooting and cost stats.

``rendered_system_prompt`` is the only authoritative evidence when
investigating prompt issues; ``model_name`` snapshots which model produced
the reply. Internal fields never leave this service through REST APIs.
"""

from datetime import datetime

from sqlalchemy import DateTime, Index, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from . import Base, BIGINT_PK_TYPE
from ..constants import (
    INDEX_REPLY_LOG_CONVERSATION_ID,
    INDEX_REPLY_LOG_CREATED_AT,
    TABLE_AI_REPLY_LOG,
)


class AiReplyLog(Base):
    __tablename__ = TABLE_AI_REPLY_LOG
    __table_args__ = (
        Index(INDEX_REPLY_LOG_CONVERSATION_ID, "conversation_id"),
        Index(INDEX_REPLY_LOG_CREATED_AT, "created_at"),
    )

    id: Mapped[int] = mapped_column(BIGINT_PK_TYPE, primary_key=True, autoincrement=True)
    request_id: Mapped[str] = mapped_column(String(64), nullable=False)
    conversation_id: Mapped[str] = mapped_column(String(128), nullable=False)
    account_id: Mapped[str] = mapped_column(String(64), nullable=False)
    item_id: Mapped[str] = mapped_column(String(64), nullable=False)
    buyer_id: Mapped[str] = mapped_column(String(64), nullable=False)
    rendered_system_prompt: Mapped[str] = mapped_column(Text, nullable=False)
    intent: Mapped[str | None] = mapped_column(String(16), nullable=True)
    model_name: Mapped[str | None] = mapped_column(String(64), nullable=True)
    user_message: Mapped[str] = mapped_column(Text, nullable=False)
    ai_reply: Mapped[str] = mapped_column(Text, nullable=False)
    prompt_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    completion_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )
