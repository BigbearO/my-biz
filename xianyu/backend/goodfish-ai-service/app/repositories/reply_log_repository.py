"""Single-table CRUD for ``ai_reply_log`` (troubleshooting evidence)."""

from sqlalchemy.ext.asyncio import AsyncSession

from ..models import AiReplyLog


class ReplyLogRepository:
    async def add(
        self,
        session: AsyncSession,
        request_id: str,
        conversation_id: str,
        account_id: str,
        item_id: str,
        buyer_id: str,
        rendered_system_prompt: str,
        intent: str | None,
        model_name: str | None,
        user_message: str,
        ai_reply: str,
        prompt_tokens: int | None = None,
        completion_tokens: int | None = None,
        latency_ms: int | None = None,
    ) -> AiReplyLog:
        reply_log = AiReplyLog(
            request_id=request_id,
            conversation_id=conversation_id,
            account_id=account_id,
            item_id=item_id,
            buyer_id=buyer_id,
            rendered_system_prompt=rendered_system_prompt,
            intent=intent,
            model_name=model_name,
            user_message=user_message,
            ai_reply=ai_reply,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            latency_ms=latency_ms,
        )
        session.add(reply_log)
        await session.flush()
        return reply_log
