import logging
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from xxs_nacos_grpc import (
    GrpcChannelRegistry,
    GrpcSettings,
    UserContextClientInterceptor,
    UserContextMiddleware,
)

from .api.health import health
from .api.llm_models import get_llm_model, list_llm_models
from .api.reply import reply, reply_validation_error_handler
from .bootstrap import BootstrapSettings
from .constants import (
    API_AI_REPLY_ROUTE,
    API_LLM_MODEL_DETAIL_ROUTE,
    API_LLM_MODELS_ROUTE,
    SERVICE_VERSION,
    UVICORN_WORKERS_SINGLE,
)
from .db import create_database_engine, create_session_maker
from .errors import ConfigEmptyError
from .grpc.item import FakeItemClient, ItemClient
from .grpc.llm import ModelClient
from .managers.conversation_manager import ConversationManager
from .managers.idempotency_manager import IdempotencyManager
from .managers.persist_manager import PersistManager
from .nacos.manager import NacosManager, resolve_register_ip
from .observability import OpenTelemetrySettings, TelemetryManager
from .redis_client import RedisPool
from .repositories.conversation_repository import ConversationRepository
from .repositories.message_repository import MessageRepository
from .repositories.reply_log_repository import ReplyLogRepository
from .runtime_config import RuntimeConfigStore, load_local_config
from .services.image_service import ImageService
from .services.intent_service import IntentService
from .services.llm_client import LlmClient
from .services.prompt_service import PromptService
from .services.reply_service import ReplyService
from .settings import BusinessSettings, warn_restart_required_sections

logger = logging.getLogger(__name__)


def load_bootstrap() -> BootstrapSettings:
    return BootstrapSettings()


def validate_runtime_config(config: dict[str, Any]) -> None:
    GrpcSettings.from_mapping(config)
    OpenTelemetrySettings.from_mapping(config)
    BusinessSettings.from_mapping(config)


def create_app(bootstrap: BootstrapSettings | None = None) -> FastAPI:
    effective_bootstrap = bootstrap or load_bootstrap()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        local_config = load_local_config(effective_bootstrap.local_config_path)
        config_store = RuntimeConfigStore(local_config)
        grpc_settings = GrpcSettings.from_mapping(config_store.config)
        telemetry_settings = OpenTelemetrySettings.from_mapping(config_store.config)
        business_settings = BusinessSettings.from_mapping(config_store.config)
        grpc_registry: GrpcChannelRegistry | None = None
        telemetry: TelemetryManager | None = None
        database_engine = None
        redis_pool: RedisPool | None = None
        nacos = NacosManager(
            effective_bootstrap,
            service_ip=resolve_register_ip(effective_bootstrap.nacos_register_ip),
        )
        await nacos.start()
        try:
            if nacos.config_enabled:
                config_store.apply_remote(
                    await nacos.get_config(),
                    effective_bootstrap.nacos_config_format,
                    validate=validate_runtime_config,
                )

                async def on_config_changed(
                    tenant: str,
                    group: str,
                    data_id: str,
                    content: str,
                ) -> None:
                    nonlocal business_settings
                    next_config = config_store.apply_remote(
                        content,
                        effective_bootstrap.nacos_config_format,
                        validate=validate_runtime_config,
                    )
                    next_grpc_settings = GrpcSettings.from_mapping(next_config)
                    next_telemetry_settings = OpenTelemetrySettings.from_mapping(
                        next_config
                    )
                    next_business_settings = BusinessSettings.from_mapping(next_config)
                    if next_telemetry_settings != telemetry_settings:
                        logger.warning(
                            "OpenTelemetry configuration changed; restart the service to apply it"
                        )
                    # Business parameters (history size, idempotency TTLs, llm
                    # timeouts, intent keywords) are read per request. Only
                    # connection-level values need a restart.
                    restart_sections = warn_restart_required_sections(
                        business_settings, next_business_settings
                    )
                    if restart_sections:
                        logger.warning(
                            "Configuration sections %s changed; restart the service to apply them",
                            restart_sections,
                        )
                    business_settings = next_business_settings
                    await grpc_registry.reload(next_grpc_settings)

                grpc_settings = GrpcSettings.from_mapping(config_store.config)
                telemetry_settings = OpenTelemetrySettings.from_mapping(
                    config_store.config
                )
                business_settings = BusinessSettings.from_mapping(config_store.config)
            elif not config_store.config:
                raise ConfigEmptyError(
                    "No effective configuration: local config is empty and NACOS_DATA_ID is empty"
                )

            telemetry = TelemetryManager(
                telemetry_settings,
                environment=effective_bootstrap.app_env,
                default_service_name=effective_bootstrap.nacos_service_name,
            )
            telemetry.start()
            grpc_interceptors = telemetry.grpc_client_interceptors()
            grpc_interceptors.append(
                UserContextClientInterceptor(grpc_settings.client.client_id),
            )
            grpc_registry = GrpcChannelRegistry(
                grpc_settings,
                nacos,
                interceptors=grpc_interceptors,
            )
            await grpc_registry.start()

            # ---- business wiring -----------------------------------------
            settings_provider = lambda: BusinessSettings.from_mapping(  # noqa: E731
                config_store.config
            )
            database_engine = create_database_engine(business_settings.database)
            session_factory = create_session_maker(database_engine)
            redis_pool = RedisPool(business_settings.redis)

            conversation_repository = ConversationRepository()
            message_repository = MessageRepository()
            reply_log_repository = ReplyLogRepository()

            item_client: ItemClient | FakeItemClient
            if business_settings.item.fake_enabled:
                logger.warning(
                    "item.fake_enabled is on: the Java item service will NOT be called"
                )
                item_client = FakeItemClient()
            else:
                item_client = ItemClient(grpc_registry)

            reply_service = ReplyService(
                item_client=item_client,
                model_client=ModelClient(grpc_registry),
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
                idempotency_manager=IdempotencyManager(redis_pool, settings_provider),
                prompt_service=PromptService(),
                intent_service=IntentService(settings_provider),
                image_service=ImageService(settings_provider),
                llm_client=LlmClient(settings_provider),
                session_factory=session_factory,
                settings_provider=settings_provider,
            )

            await nacos.register_http_instance()
            app.state.bootstrap = effective_bootstrap
            app.state.runtime_config_store = config_store
            app.state.grpc_registry = grpc_registry
            app.state.telemetry_manager = telemetry
            app.state.service_version = SERVICE_VERSION
            app.state.database_engine = database_engine
            app.state.redis_pool = redis_pool
            app.state.reply_service = reply_service
            if nacos.config_enabled:
                await nacos.watch_config(on_config_changed)
            logger.info("Service startup completed")
            yield
        finally:
            await nacos.deregister_all()
            if grpc_registry is not None:
                await grpc_registry.close()
            await nacos.shutdown()
            if telemetry is not None:
                await telemetry.close()
            if redis_pool is not None:
                await redis_pool.close()
            if database_engine is not None:
                await database_engine.dispose()
            logger.info("Service shutdown completed")

    app = FastAPI(lifespan=lifespan)
    app.add_middleware(UserContextMiddleware)
    FastAPIInstrumentor.instrument_app(app)
    app.add_exception_handler(RequestValidationError, reply_validation_error_handler)
    app.add_api_route("/health", health, methods=["GET"])
    app.add_api_route(API_LLM_MODELS_ROUTE, list_llm_models, methods=["GET"])
    app.add_api_route(API_LLM_MODEL_DETAIL_ROUTE, get_llm_model, methods=["GET"])
    app.add_api_route(API_AI_REPLY_ROUTE, reply, methods=["POST"])
    return app


app = create_app()


def run() -> None:
    import uvicorn

    runtime_bootstrap = load_bootstrap()
    uvicorn.run(
        "app.main:app",
        host=runtime_bootstrap.server_host,
        port=runtime_bootstrap.server_port,
        workers=UVICORN_WORKERS_SINGLE,
    )


if __name__ == "__main__":
    run()
