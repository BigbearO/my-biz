"""Reply pipeline tests: happy path, error codes, idempotency, persistence.

These tests are written but intentionally not executed in this batch; they
run offline through the fakes in ``conftest.py``.
"""

from collections.abc import Callable
from typing import Any

import pytest
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.constants import (
    IDEMPOTENCY_KEY_PREFIX,
    INTENT_PRICE,
    MESSAGE_TYPE_IMAGE,
    ROLE_ASSISTANT,
    ROLE_USER,
)
from app.errors import (
    IdempotencyInProgressError,
    ItemNotFoundError,
    ItemPromptEmptyError,
    LlmCallFailedError,
    NoEnabledModelError,
)
from app.models import AiConversation, AiConversationMessage, AiReplyLog
from app.repositories.conversation_repository import ConversationRepository
from app.repositories.message_repository import MessageRepository
from app.repositories.reply_log_repository import ReplyLogRepository
from app.services.reply_service import ReplyService
from app.settings import BusinessSettings

from .conftest import (
    FakeItemClient,
    FakeLlmClient,
    FakeModelClient,
    build_reply_service,
    make_item_runtime_config,
)

REQUEST_ID = "req-0001"
ITEM_ID = "item-1"
BUYER_ID = "buyer-1"
ACCOUNT_ID = "account-1"
MESSAGE = "这个多少钱？"
CONVERSATION_ID = f"{ITEM_ID}_{BUYER_ID}"


def _build(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
    *,
    item_client: FakeItemClient | None = None,
    model_client: FakeModelClient | None = None,
    llm_client: FakeLlmClient | None = None,
) -> ReplyService:
    return build_reply_service(
        session_factory=session_factory,
        settings_provider=settings_provider,
        redis_client=fake_redis,
        conversation_repository=conversation_repository,
        message_repository=message_repository,
        reply_log_repository=reply_log_repository,
        item_client=item_client,
        model_client=model_client,
        llm_client=llm_client,
    )


async def _reply(
    service: ReplyService,
    *,
    request_id: str = REQUEST_ID,
    image_urls: list[str] | None = None,
) -> dict[str, Any]:
    return await service.reply(
        request_id=request_id,
        goodfish_item_id=ITEM_ID,
        buyer_id=BUYER_ID,
        goodfish_account_id=ACCOUNT_ID,
        message=MESSAGE,
        image_urls=image_urls or [],
    )


@pytest.mark.asyncio
async def test_reply_success_persists_message_conversation_and_log(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    llm = FakeLlmClient()
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        llm_client=llm,
    )

    data = await _reply(service)

    assert data["reply"] == llm.reply
    assert data["conversation_id"] == CONVERSATION_ID
    assert data["model_name"] == "fake-model"
    assert data["latency_ms"] == 321

    async with session_factory() as session:
        conversations = (await session.execute(select(AiConversation))).scalars().all()
        messages = (await session.execute(select(AiConversationMessage))).scalars().all()
        logs = (await session.execute(select(AiReplyLog))).scalars().all()

    assert len(conversations) == 1
    assert conversations[0].conversation_id == CONVERSATION_ID
    assert conversations[0].goodfish_item_id == ITEM_ID
    assert conversations[0].buyer_id == BUYER_ID
    assert {message.role for message in messages} == {ROLE_USER, ROLE_ASSISTANT}
    assert len(messages) == 2
    assert len(logs) == 1
    assert logs[0].request_id == REQUEST_ID
    assert logs[0].prompt_tokens == 120
    assert logs[0].completion_tokens == 24


@pytest.mark.asyncio
async def test_system_prompt_renders_item_fields_and_question(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    llm = FakeLlmClient()
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        llm_client=llm,
    )

    await _reply(service)

    system_message = llm.calls[0]["messages"][0]
    assert system_message["role"] == "system"
    content = system_message["content"]
    assert "测试商品" in content
    assert "99.00" in content
    assert MESSAGE in content
    assert "{{" not in content


@pytest.mark.asyncio
async def test_idempotent_replay_returns_cache_and_skips_downstream(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    item = FakeItemClient()
    llm = FakeLlmClient()
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        item_client=item,
        llm_client=llm,
    )

    first = await _reply(service)
    second = await _reply(service)

    assert second == first
    assert len(item.calls) == 1
    assert len(llm.calls) == 1

    async with session_factory() as session:
        messages = (await session.execute(select(AiConversationMessage))).scalars().all()
    assert len(messages) == 2


@pytest.mark.asyncio
async def test_placeholder_conflict_maps_to_service_unavailable(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    await fake_redis.set(f"{IDEMPOTENCY_KEY_PREFIX}{REQUEST_ID}", "processing", ex=100)
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
    )

    with pytest.raises(IdempotencyInProgressError) as error:
        await _reply(service)

    assert error.value.code == 50301


@pytest.mark.asyncio
async def test_failure_releases_placeholder_for_retry(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    item = FakeItemClient()
    item.error = ItemNotFoundError("商品不存在")
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        item_client=item,
    )

    with pytest.raises(ItemNotFoundError):
        await _reply(service)

    assert await fake_redis.get(f"{IDEMPOTENCY_KEY_PREFIX}{REQUEST_ID}") is None

    item.error = None
    data = await _reply(service)
    assert data["conversation_id"] == CONVERSATION_ID


@pytest.mark.asyncio
async def test_empty_system_prompt_skips_llm(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    item = FakeItemClient()
    item.result = make_item_runtime_config(system_prompt="   ")
    llm = FakeLlmClient()
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        item_client=item,
        llm_client=llm,
    )

    with pytest.raises(ItemPromptEmptyError) as error:
        await _reply(service)

    assert error.value.code == 40402
    assert llm.calls == []


@pytest.mark.asyncio
async def test_no_enabled_model_maps_to_50002_without_persisting(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    model = FakeModelClient()
    model.error = NoEnabledModelError("无启用的模型")
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        model_client=model,
    )

    with pytest.raises(NoEnabledModelError) as error:
        await _reply(service)

    assert error.value.code == 50002
    async with session_factory() as session:
        messages = (await session.execute(select(AiConversationMessage))).scalars().all()
        logs = (await session.execute(select(AiReplyLog))).scalars().all()
    assert messages == []
    assert logs == []


@pytest.mark.asyncio
async def test_llm_failure_does_not_persist_and_releases_placeholder(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    llm = FakeLlmClient()
    llm.error = LlmCallFailedError("大模型调用失败")
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        llm_client=llm,
    )

    with pytest.raises(LlmCallFailedError) as error:
        await _reply(service)

    assert error.value.code == 50001
    assert await fake_redis.get(f"{IDEMPOTENCY_KEY_PREFIX}{REQUEST_ID}") is None
    async with session_factory() as session:
        messages = (await session.execute(select(AiConversationMessage))).scalars().all()
    assert messages == []


@pytest.mark.asyncio
async def test_history_is_replayed_into_messages(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    first_service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
    )
    await _reply(first_service, request_id="req-first")

    llm = FakeLlmClient()
    second_service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        llm_client=llm,
    )
    await _reply(second_service, request_id="req-second")

    sent = llm.calls[0]["messages"]
    # system + previous user + previous assistant
    assert len(sent) == 3
    assert sent[1]["role"] == ROLE_USER
    assert sent[2]["role"] == ROLE_ASSISTANT


@pytest.mark.asyncio
async def test_missing_image_degrades_to_placeholder(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    llm = FakeLlmClient()
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        llm_client=llm,
    )

    await _reply(
        service,
        image_urls=[
            "http://127.0.0.1:8000/api/ai/image/view/0123456789abcdef0123456789abcdef.png"
        ],
    )

    sent = llm.calls[0]["messages"]
    # Only the system prompt: no multimodal user message for a missing image.
    assert len(sent) == 1
    async with session_factory() as session:
        messages = (await session.execute(select(AiConversationMessage))).scalars().all()
    user_message = next(message for message in messages if message.role == ROLE_USER)
    assert "[图片]" in user_message.content
    assert user_message.message_type != MESSAGE_TYPE_IMAGE


@pytest.mark.asyncio
async def test_intent_is_persisted_from_keywords(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
    )

    await _reply(service)

    async with session_factory() as session:
        logs = (await session.execute(select(AiReplyLog))).scalars().all()
    assert logs[0].intent == INTENT_PRICE


@pytest.mark.asyncio
async def test_persistence_failure_rolls_back_all_tables(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def boom(*args: Any, **kwargs: Any) -> Any:
        raise RuntimeError("reply log insert failed")

    monkeypatch.setattr(reply_log_repository, "add", boom)
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
    )

    with pytest.raises(RuntimeError):
        await _reply(service)

    async with session_factory() as session:
        messages = (await session.execute(select(AiConversationMessage))).scalars().all()
        logs = (await session.execute(select(AiReplyLog))).scalars().all()
    assert messages == []
    assert logs == []
    # The placeholder must be released so a retry is not blocked.
    assert await fake_redis.get(f"{IDEMPOTENCY_KEY_PREFIX}{REQUEST_ID}") is None


@pytest.mark.asyncio
async def test_product_system_prompt_text_is_used_verbatim(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    item = FakeItemClient()
    item.result = make_item_runtime_config(
        system_prompt="你是只卖二手机的客服，只谈这台机器的成色。{{question}}",
    )
    llm = FakeLlmClient()
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        item_client=item,
        llm_client=llm,
    )

    await _reply(service)

    content = llm.calls[0]["messages"][0]["content"]
    # The operator's wording must survive; no built-in template may override it.
    assert "你是只卖二手机的客服" in content
    assert MESSAGE in content
    assert "闲鱼客服AI助手。请根据用户消息和上下文" not in content


@pytest.mark.asyncio
async def test_question_section_appended_when_placeholder_missing(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    item = FakeItemClient()
    item.result = make_item_runtime_config(
        system_prompt="你是一位专业的闲鱼客服AI助手，只依据商品信息回答。",
    )
    llm = FakeLlmClient()
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        item_client=item,
        llm_client=llm,
    )

    await _reply(service)

    content = llm.calls[0]["messages"][0]["content"]
    assert "## 买家问题" in content
    assert MESSAGE in content


@pytest.mark.asyncio
async def test_scenario_sections_only_include_configured_prompts(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> None:
    item = FakeItemClient()
    llm = FakeLlmClient()
    service = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        item_client=item,
        llm_client=llm,
    )

    # Default: all three scenario prompts are blank -> no scenario section.
    await _reply(service)
    assert "## 场景处理指南" not in llm.calls[0]["messages"][0]["content"]

    # Configured: only the price scenario appears, blank ones are omitted.
    item.result = make_item_runtime_config(
        price_prompt="最多让 5 元，超过就礼貌拒绝。",
        tech_prompt="",
        default_prompt="",
    )
    scenario_llm = FakeLlmClient()
    service_with_scenarios = _build(
        session_factory,
        settings_provider,
        fake_redis,
        conversation_repository,
        message_repository,
        reply_log_repository,
        item_client=item,
        llm_client=scenario_llm,
    )
    await _reply(service_with_scenarios, request_id="req-scenario")

    content = scenario_llm.calls[0]["messages"][0]["content"]
    assert "## 场景处理指南" in content
    assert "最多让 5 元" in content
    assert "### 当用户询问产品技术/功能/使用问题时" not in content
