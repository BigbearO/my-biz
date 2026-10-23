"""Three-stage reply orchestration (AI reply design section 2.2).

Stage 1: item runtime config over gRPC.
Stage 2: local context assembly (conversation, history, intent, prompt).
Stage 3: enabled model over gRPC, then the OpenAI-compatible HTTP call,
single-transaction persistence, idempotency result, response.

Any failure after the idempotency placeholder is acquired releases the
placeholder, otherwise client retries would be blocked until TTL expiry.
"""

import logging
from collections.abc import Callable
from typing import Any

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from ..constants import (
    CONVERSATION_ID_SEPARATOR,
    IMAGE_PLACEHOLDER_TEXT,
    MESSAGE_IDEMPOTENCY_IN_PROGRESS,
    MESSAGE_ITEM_PROMPT_EMPTY,
    MESSAGE_TYPE_IMAGE,
    MESSAGE_TYPE_TEXT,
    OPENAI_ROLE_SYSTEM,
    OPENAI_ROLE_USER,
    OPENAI_TYPE_IMAGE_URL,
    ROLE_USER,
)
from ..errors import (
    IdempotencyInProgressError,
    ItemPromptEmptyError,
)
from ..grpc.item import FakeItemClient, ItemClient
from ..grpc.llm import ModelClient
from ..managers.conversation_manager import ConversationManager
from ..managers.idempotency_manager import IdempotencyManager
from ..managers.persist_manager import PersistManager
from ..settings import BusinessSettings
from .image_service import ImageService
from .intent_service import IntentService
from .llm_client import LlmClient
from .prompt_service import PromptInputs, PromptService

logger = logging.getLogger(__name__)


class ReplyService:
    def __init__(
        self,
        *,
        item_client: ItemClient | FakeItemClient,
        model_client: ModelClient,
        conversation_manager: ConversationManager,
        persist_manager: PersistManager,
        idempotency_manager: IdempotencyManager,
        prompt_service: PromptService,
        intent_service: IntentService,
        image_service: ImageService,
        llm_client: LlmClient,
        session_factory: async_sessionmaker[AsyncSession],
        settings_provider: Callable[[], BusinessSettings],
    ) -> None:
        self._item_client = item_client
        self._model_client = model_client
        self._conversations = conversation_manager
        self._persist = persist_manager
        self._idempotency = idempotency_manager
        self._prompts = prompt_service
        self._intents = intent_service
        self._images = image_service
        self._llm = llm_client
        self._session_factory = session_factory
        self._settings_provider = settings_provider

    async def reply(
        self,
        *,
        request_id: str,
        goodfish_item_id: str,
        buyer_id: str,
        goodfish_account_id: str,
        message: str,
        image_urls: list[str],
    ) -> dict[str, Any]:
        settings = self._settings_provider()

        cached = await self._idempotency.get_cached_response(request_id)
        if cached is not None:
            return cached

        if not await self._idempotency.try_acquire_placeholder(request_id):
            raise IdempotencyInProgressError(MESSAGE_IDEMPOTENCY_IN_PROGRESS)

        try:
            return await self._reply_inner(
                settings=settings,
                request_id=request_id,
                goodfish_item_id=goodfish_item_id,
                buyer_id=buyer_id,
                goodfish_account_id=goodfish_account_id,
                message=message,
                image_urls=image_urls,
            )
        except Exception:
            await self._idempotency.release_placeholder(request_id)
            raise

    async def _reply_inner(
        self,
        *,
        settings: BusinessSettings,
        request_id: str,
        goodfish_item_id: str,
        buyer_id: str,
        goodfish_account_id: str,
        message: str,
        image_urls: list[str],
    ) -> dict[str, Any]:
        # ---- Stage 1: item runtime config --------------------------------
        item = await self._item_client.get_item_runtime_config(
            account_id=goodfish_account_id,
            item_id=goodfish_item_id,
        )
        prompt_config = item.prompt
        if not (prompt_config.system_prompt or "").strip():
            raise ItemPromptEmptyError(MESSAGE_ITEM_PROMPT_EMPTY)

        # ---- Stage 2: local context assembly -----------------------------
        conversation_id = (
            f"{goodfish_item_id}{CONVERSATION_ID_SEPARATOR}{buyer_id}"
        )
        async with self._session_factory() as session:
            await self._conversations.get_or_create(
                session,
                conversation_id=conversation_id,
                goodfish_item_id=goodfish_item_id,
                buyer_id=buyer_id,
                goodfish_account_id=goodfish_account_id,
            )
            history = await self._conversations.recent_messages(
                session,
                conversation_id,
                settings.conversation.max_history_messages,
            )

        intent = self._intents.detect(message)
        rendered_system_prompt = self._prompts.render_system_prompt(
            PromptInputs(
                item_title=item.title,
                item_desc=item.description,
                item_price=item.price,
                max_reply_length=prompt_config.max_reply_length,
                price_prompt=prompt_config.price_prompt,
                tech_prompt=prompt_config.tech_prompt,
                default_prompt=prompt_config.default_prompt,
                question=message,
            )
        )

        # Image validation: existing urls feed the model, missing ones
        # degrade to a placeholder appended to the stored user message.
        existing_images, degraded = self._images.validate_urls(image_urls)
        user_content = " ".join(part for part in [message.strip(), *degraded] if part)
        user_message_type = MESSAGE_TYPE_IMAGE if existing_images else MESSAGE_TYPE_TEXT

        # ---- Stage 3: model call, persistence ----------------------------
        enabled_model = await self._model_client.get_enabled_model()

        messages: list[dict[str, Any]] = [
            {"role": OPENAI_ROLE_SYSTEM, "content": rendered_system_prompt}
        ]
        for history_message in history:
            messages.append(self._history_message(history_message))
        if existing_images:
            messages.append(
                {
                    "role": OPENAI_ROLE_USER,
                    "content": [
                        {
                            "type": OPENAI_TYPE_IMAGE_URL,
                            "image_url": {"url": url},
                        }
                        for url in existing_images
                    ],
                }
            )

        result = await self._llm.chat(
            base_url=enabled_model.base_url,
            api_key=enabled_model.api_key,
            model_name=enabled_model.model_name,
            temperature=enabled_model.temperature,
            max_tokens=enabled_model.max_tokens,
            messages=messages,
        )

        await self._persist.persist_reply(
            conversation_id=conversation_id,
            request_id=request_id,
            user_message=user_content or IMAGE_PLACEHOLDER_TEXT,
            user_message_type=user_message_type,
            assistant_reply=result.reply,
            model_name=enabled_model.model_name,
            intent=intent,
            rendered_system_prompt=rendered_system_prompt,
            account_id=goodfish_account_id,
            item_id=goodfish_item_id,
            buyer_id=buyer_id,
            prompt_tokens=result.prompt_tokens,
            completion_tokens=result.completion_tokens,
            latency_ms=result.latency_ms,
        )

        response_data = {
            "reply": result.reply,
            "conversation_id": conversation_id,
            "model_name": enabled_model.model_name,
            "latency_ms": result.latency_ms,
        }
        await self._idempotency.store_result(request_id, response_data)
        return response_data

    @staticmethod
    def _history_message(history_message: Any) -> dict[str, Any]:
        """Replay one history row; image user rows become multimodal."""
        if (
            history_message.role == ROLE_USER
            and history_message.message_type == MESSAGE_TYPE_IMAGE
            and history_message.content
        ):
            return {
                "role": ROLE_USER,
                "content": [
                    {
                        "type": OPENAI_TYPE_IMAGE_URL,
                        "image_url": {"url": history_message.content},
                    }
                ],
            }
        return {"role": history_message.role, "content": history_message.content}
