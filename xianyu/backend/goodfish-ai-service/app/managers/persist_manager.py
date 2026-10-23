"""Single-transaction persistence for one completed reply.

Writes, inside one transaction: the buyer message, the assistant reply
(with tokens/latency), the conversation's ``last_message_at`` and the
``ai_reply_log`` decision record. Any failure rolls the whole thing back;
the reply is either fully recorded or not at all.
"""

from datetime import datetime, timezone
from typing import Any

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from ..constants import (
    MESSAGE_TYPE_TEXT,
    ROLE_ASSISTANT,
    ROLE_USER,
)
from ..repositories.conversation_repository import ConversationRepository
from ..repositories.message_repository import MessageRepository
from ..repositories.reply_log_repository import ReplyLogRepository


class PersistManager:
    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        conversation_repository: ConversationRepository,
        message_repository: MessageRepository,
        reply_log_repository: ReplyLogRepository,
    ) -> None:
        self._session_factory = session_factory
        self._conversations = conversation_repository
        self._messages = message_repository
        self._reply_logs = reply_log_repository

    async def persist_reply(
        self,
        *,
        conversation_id: str,
        request_id: str,
        user_message: str,
        user_message_type: str,
        assistant_reply: str,
        model_name: str,
        intent: str | None,
        rendered_system_prompt: str,
        account_id: str,
        item_id: str,
        buyer_id: str,
        prompt_tokens: int | None = None,
        completion_tokens: int | None = None,
        latency_ms: int | None = None,
    ) -> None:
        async with self._session_factory() as session:
            async with session.begin():
                await self._messages.add(
                    session,
                    conversation_id=conversation_id,
                    role=ROLE_USER,
                    content=user_message,
                    message_type=user_message_type,
                    request_id=request_id,
                )
                await self._messages.add(
                    session,
                    conversation_id=conversation_id,
                    role=ROLE_ASSISTANT,
                    content=assistant_reply,
                    message_type=MESSAGE_TYPE_TEXT,
                    request_id=request_id,
                    model_name=model_name,
                    prompt_tokens=prompt_tokens,
                    completion_tokens=completion_tokens,
                    latency_ms=latency_ms,
                )
                now = datetime.now(timezone.utc)
                await self._conversations.touch_last_message_at(session, conversation_id, now)
                await self._reply_logs.add(
                    session,
                    request_id=request_id,
                    conversation_id=conversation_id,
                    account_id=account_id,
                    item_id=item_id,
                    buyer_id=buyer_id,
                    rendered_system_prompt=rendered_system_prompt,
                    intent=intent,
                    model_name=model_name,
                    user_message=user_message,
                    ai_reply=assistant_reply,
                    prompt_tokens=prompt_tokens,
                    completion_tokens=completion_tokens,
                    latency_ms=latency_ms,
                )
