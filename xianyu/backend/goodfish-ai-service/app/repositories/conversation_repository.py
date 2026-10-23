"""Single-table CRUD for ``ai_conversation``. No business decisions here."""

from datetime import datetime

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from ..models import AiConversation


class ConversationRepository:
    async def get_by_conversation_id(
        self,
        session: AsyncSession,
        conversation_id: str,
    ) -> AiConversation | None:
        result = await session.execute(
            select(AiConversation).where(AiConversation.conversation_id == conversation_id)
        )
        return result.scalar_one_or_none()

    async def create(
        self,
        session: AsyncSession,
        conversation_id: str,
        goodfish_item_id: str,
        buyer_id: str,
        goodfish_account_id: str,
    ) -> AiConversation:
        conversation = AiConversation(
            conversation_id=conversation_id,
            goodfish_item_id=goodfish_item_id,
            buyer_id=buyer_id,
            goodfish_account_id=goodfish_account_id,
        )
        session.add(conversation)
        await session.flush()
        return conversation

    async def touch_last_message_at(
        self,
        session: AsyncSession,
        conversation_id: str,
        last_message_at: datetime,
    ) -> None:
        await session.execute(
            update(AiConversation)
            .where(AiConversation.conversation_id == conversation_id)
            .values(last_message_at=last_message_at, updated_at=last_message_at)
        )
