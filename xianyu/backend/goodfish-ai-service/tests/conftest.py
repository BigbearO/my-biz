"""Shared fakes for the conversation reply pipeline tests.

No external dependency is touched: SQLite in-memory for PostgreSQL, fakeredis
for Redis, hand-rolled stubs for the item/model gRPC services and the LLM
HTTP gateway.
"""

from collections.abc import AsyncIterator, Callable
from typing import Any

import fakeredis.aioredis
import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.pool import StaticPool

from app.managers.conversation_manager import ConversationManager
from app.managers.idempotency_manager import IdempotencyManager
from app.managers.persist_manager import PersistManager
from app.models import Base
from app.pb.item.v1 import item_pb2
from app.repositories.conversation_repository import ConversationRepository
from app.repositories.message_repository import MessageRepository
from app.repositories.reply_log_repository import ReplyLogRepository
from app.services.image_service import ImageService
from app.services.intent_service import IntentService
from app.services.llm_client import LlmCallResult
from app.services.prompt_service import PromptService
from app.services.reply_service import ReplyService
from app.settings import BusinessSettings


class FakeRedisPool:
    """Minimal stand-in exposing the ``client`` attribute RedisPool offers."""

    def __init__(self, client: Any) -> None:
        self.client = client

    async def close(self) -> None:
        return None


class FakeItemClient:
    def __init__(self) -> None:
        self.result: Any = make_item_runtime_config()
        self.error: Exception | None = None
        self.calls: list[tuple[str, str]] = []

    async def get_item_runtime_config(self, account_id: str, item_id: str) -> Any:
        self.calls.append((account_id, item_id))
        if self.error is not None:
            raise self.error
        return self.result


class FakeModelClient:
    def __init__(self) -> None:
        self.error: Exception | None = None
        self.model_name = "fake-model"

    async def get_enabled_model(self) -> Any:
        if self.error is not None:
            raise self.error
        return _EnabledModel(
            base_url="http://llm.local/v1",
            api_key="sk-test",
            model_name=self.model_name,
            temperature=0.7,
            max_tokens=512,
        )


class _EnabledModel:
    """Duck-typed stand-in for llm.v1.EnabledModel."""

    def __init__(
        self,
        base_url: str,
        api_key: str,
        model_name: str,
        temperature: float,
        max_tokens: int,
    ) -> None:
        self.base_url = base_url
        self.api_key = api_key
        self.model_name = model_name
        self.temperature = temperature
        self.max_tokens = max_tokens


class FakeLlmClient:
    def __init__(self) -> None:
        self.error: Exception | None = None
        self.reply = "这个50元包邮，要的话直接拍~"
        self.calls: list[dict[str, Any]] = []

    async def chat(
        self,
        *,
        base_url: str,
        api_key: str,
        model_name: str,
        temperature: float,
        max_tokens: int,
        messages: list[dict],
    ) -> LlmCallResult:
        self.calls.append({"model_name": model_name, "messages": messages})
        if self.error is not None:
            raise self.error
        return LlmCallResult(
            reply=self.reply,
            prompt_tokens=120,
            completion_tokens=24,
            latency_ms=321,
        )


DEFAULT_SYSTEM_PROMPT = (
    "你是一位专业的闲鱼客服AI助手。\n"
    "## 商品信息\n"
    "标题：{{item_title}}\n"
    "描述：{{item_desc}}\n"
    "价格：{{item_price}}\n"
    "## 核心原则\n"
    "回复不超过{{max_reply_length}}个字。\n"
    "## 买家问题\n"
    "{{question}}"
)


def make_item_runtime_config(
    *,
    system_prompt: str = DEFAULT_SYSTEM_PROMPT,
    max_reply_length: int = 50,
    price_prompt: str = "",
    tech_prompt: str = "",
    default_prompt: str = "",
) -> Any:
    """Build a real item.v1.ItemRuntimeConfig; scenario prompts stay blank by default."""
    return item_pb2.ItemRuntimeConfig(
        item_id="item-1",
        title="测试商品",
        description="九成新",
        price="99.00",
        stock_status="on_sale",
        image_url="",
        prompt=item_pb2.PromptConfig(
            system_prompt=system_prompt,
            price_prompt=price_prompt,
            tech_prompt=tech_prompt,
            default_prompt=default_prompt,
            max_reply_length=max_reply_length,
        ),
    )


@pytest.fixture
def business_settings() -> BusinessSettings:
    return BusinessSettings.from_mapping({})


@pytest.fixture
def settings_provider(business_settings: BusinessSettings) -> Callable[[], BusinessSettings]:
    return lambda: business_settings


@pytest_asyncio.fixture
async def session_factory() -> AsyncIterator[async_sessionmaker[AsyncSession]]:
    engine = create_async_engine(
        "sqlite+aiosqlite://",
        poolclass=StaticPool,
        connect_args={"check_same_thread": False},
    )
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, expire_on_commit=False, autoflush=False)
    try:
        yield factory
    finally:
        await engine.dispose()


@pytest_asyncio.fixture
async def fake_redis() -> AsyncIterator[Any]:
    client = fakeredis.aioredis.FakeRedis(decode_responses=True)
    try:
        yield client
    finally:
        await client.aclose()


@pytest.fixture
def conversation_repository() -> ConversationRepository:
    return ConversationRepository()


@pytest.fixture
def message_repository() -> MessageRepository:
    return MessageRepository()


@pytest.fixture
def reply_log_repository() -> ReplyLogRepository:
    return ReplyLogRepository()


@pytest_asyncio.fixture
async def reply_service(
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    fake_redis: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
) -> AsyncIterator[ReplyService]:
    service = build_reply_service(
        session_factory=session_factory,
        settings_provider=settings_provider,
        redis_client=fake_redis,
        conversation_repository=conversation_repository,
        message_repository=message_repository,
        reply_log_repository=reply_log_repository,
    )
    yield service


def build_reply_service(
    *,
    session_factory: async_sessionmaker[AsyncSession],
    settings_provider: Callable[[], BusinessSettings],
    redis_client: Any,
    conversation_repository: ConversationRepository,
    message_repository: MessageRepository,
    reply_log_repository: ReplyLogRepository,
    item_client: FakeItemClient | None = None,
    model_client: FakeModelClient | None = None,
    llm_client: FakeLlmClient | None = None,
) -> ReplyService:
    """Wire a ReplyService with fakes; tests may pass their own stub instances."""
    return ReplyService(
        item_client=item_client or FakeItemClient(),
        model_client=model_client or FakeModelClient(),
        conversation_manager=ConversationManager(
            conversation_repository,
            message_repository,
        ),
        persist_manager=PersistManager(
            session_factory,
            conversation_repository,
            message_repository,
            reply_log_repository,
        ),
        idempotency_manager=IdempotencyManager(
            FakeRedisPool(redis_client),
            settings_provider,
        ),
        prompt_service=PromptService(),
        intent_service=IntentService(settings_provider),
        image_service=ImageService(settings_provider),
        llm_client=llm_client or FakeLlmClient(),
        session_factory=session_factory,
        settings_provider=settings_provider,
    )
