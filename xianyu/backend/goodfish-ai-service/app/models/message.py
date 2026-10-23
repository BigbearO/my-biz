"""``ai_conversation_message``: one row per user/assistant message.

Image messages store the local image URL in ``content`` (never base64).
"""

from datetime import datetime

from sqlalchemy import DateTime, Index, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from . import Base, BIGINT_PK_TYPE
from ..constants import (
    INDEX_MESSAGE_CONVERSATION_ID,
    TABLE_AI_CONVERSATION_MESSAGE,
)


class AiConversationMessage(Base):
    __tablename__ = TABLE_AI_CONVERSATION_MESSAGE
    __table_args__ = (
        Index(
            INDEX_MESSAGE_CONVERSATION_ID,
            "conversation_id",
            "created_at",
        ),
    )

    id: Mapped[int] = mapped_column(BIGINT_PK_TYPE, primary_key=True, autoincrement=True)
    conversation_id: Mapped[str] = mapped_column(String(128), nullable=False)
    role: Mapped[str] = mapped_column(String(16), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    message_type: Mapped[str] = mapped_column(String(16), nullable=False)
    request_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    model_name: Mapped[str | None] = mapped_column(String(64), nullable=True)
    prompt_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    completion_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )
