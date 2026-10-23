"""Conversation lifecycle: get-or-create plus context retrieval."""

import logging

from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from ..models import AiConversation, AiConversationMessage
from ..repositories.conversation_repository import ConversationRepository
from ..repositories.message_repository import MessageRepository

logger = logging.getLogger(__name__)


class ConversationManager:
    def __init__(
        self,
        conversation_repository: ConversationRepository,
        message_repository: MessageRepository,
    ) -> None:
        self._conversations = conversation_repository
        self._messages = message_repository

    async def get_or_create(
        self,
        session: AsyncSession,
        conversation_id: str,
        goodfish_item_id: str,
        buyer_id: str,
        goodfish_account_id: str,
    ) -> AiConversation:
        """Return the conversation, creating it on first contact.

        Two workers may create the same conversation concurrently; the
        unique index turns the loser into a plain re-read.
        """
        existing = await self._conversations.get_by_conversation_id(session, conversation_id)
        if existing is not None:
            return existing
        try:
            return await self._conversations.create(
                session,
                conversation_id=conversation_id,
                goodfish_item_id=goodfish_item_id,
                buyer_id=buyer_id,
                goodfish_account_id=goodfish_account_id,
            )
        except IntegrityError:
            await session.rollback()
            existing = await self._conversations.get_by_conversation_id(session, conversation_id)
            if existing is None:
                raise
            return existing

    async def recent_messages(
        self,
        session: AsyncSession,
        conversation_id: str,
        limit: int,
    ) -> list[AiConversationMessage]:
        return await self._messages.get_recent(session, conversation_id, limit)
