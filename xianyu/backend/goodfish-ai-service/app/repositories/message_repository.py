"""Single-table CRUD for ``ai_conversation_message``."""

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from ..constants import ROLE_ASSISTANT, ROLE_USER
from ..models import AiConversationMessage


class MessageRepository:
    async def add(
        self,
        session: AsyncSession,
        conversation_id: str,
        role: str,
        content: str,
        message_type: str,
        request_id: str | None = None,
        model_name: str | None = None,
        prompt_tokens: int | None = None,
        completion_tokens: int | None = None,
        latency_ms: int | None = None,
    ) -> AiConversationMessage:
        message = AiConversationMessage(
            conversation_id=conversation_id,
            role=role,
            content=content,
            message_type=message_type,
            request_id=request_id,
            model_name=model_name,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            latency_ms=latency_ms,
        )
        session.add(message)
        await session.flush()
        return message

    async def get_recent(
        self,
        session: AsyncSession,
        conversation_id: str,
        limit: int,
    ) -> list[AiConversationMessage]:
        """Return the most recent messages in chronological order."""
        result = await session.execute(
            select(AiConversationMessage)
            .where(AiConversation.conversation_id == conversation_id)
            .order_by(AiConversationMessage.created_at.desc(), AiConversationMessage.id.desc())
            .limit(limit)
        )
        return list(reversed(result.scalars().all()))

    async def count_recent(
        self,
        session: AsyncSession,
        conversation_id: str,
    ) -> int:
        result = await session.execute(
            select(func.count())
            .select_from(AiConversationMessage)
            .where(AiConversationMessage.conversation_id == conversation_id)
        )
        return int(result.scalar_one())

    @staticmethod
    def is_context_role(role: str) -> bool:
        """Only user/assistant history is replayed into the LLM context."""
        return role in (ROLE_USER, ROLE_ASSISTANT)
