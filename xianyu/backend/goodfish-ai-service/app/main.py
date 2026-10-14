import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI
from xxs_nacos_grpc import (
    GrpcChannelRegistry,
    GrpcSettings,
    UserContextClientInterceptor,
    UserContextMiddleware,
)

from .api.health import health
from .api.llm_models import get_llm_model, list_llm_models
from .bootstrap import BootstrapSettings
from .constants import (
    API_LLM_MODEL_DETAIL_ROUTE,
    API_LLM_MODELS_ROUTE,
    SERVICE_VERSION,
    UVICORN_WORKERS_SINGLE,
)
from .errors import ConfigEmptyError
from .nacos.manager import NacosManager, resolve_register_ip
from .runtime_config import RuntimeConfigStore, load_local_config

logger = logging.getLogger(__name__)


def load_bootstrap() -> BootstrapSettings:
    return BootstrapSettings()


def create_app(bootstrap: BootstrapSettings | None = None) -> FastAPI:
    effective_bootstrap = bootstrap or load_bootstrap()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        local_config = load_local_config(effective_bootstrap.local_config_path)
        config_store = RuntimeConfigStore(local_config)
        grpc_settings = GrpcSettings.from_mapping(config_store.config)
        grpc_registry: GrpcChannelRegistry | None = None
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
                    validate=GrpcSettings.from_mapping,
                )

                async def on_config_changed(
                    tenant: str,
                    group: str,
                    data_id: str,
                    content: str,
                ) -> None:
                    next_config = config_store.apply_remote(
                        content,
                        effective_bootstrap.nacos_config_format,
                        validate=GrpcSettings.from_mapping,
                    )
                    next_grpc_settings = GrpcSettings.from_mapping(next_config)
                    await grpc_registry.reload(next_grpc_settings)

                grpc_settings = GrpcSettings.from_mapping(config_store.config)
            elif not config_store.config:
                raise ConfigEmptyError(
                    "No effective configuration: local config is empty and NACOS_DATA_ID is empty"
                )

            grpc_registry = GrpcChannelRegistry(
                grpc_settings,
                nacos,
                interceptors=[
                    UserContextClientInterceptor(grpc_settings.client.client_id),
                ],
            )
            await grpc_registry.start()
            await nacos.register_http_instance()
            app.state.bootstrap = effective_bootstrap
            app.state.runtime_config_store = config_store
            app.state.grpc_registry = grpc_registry
            app.state.service_version = SERVICE_VERSION
            if nacos.config_enabled:
                await nacos.watch_config(on_config_changed)
            logger.info("Service startup completed")
            yield
        finally:
            await nacos.deregister_all()
            if grpc_registry is not None:
                await grpc_registry.close()
            await nacos.shutdown()
            logger.info("Service shutdown completed")

    app = FastAPI(lifespan=lifespan)
    app.add_middleware(UserContextMiddleware)
    app.add_api_route("/health", health, methods=["GET"])
    app.add_api_route(API_LLM_MODELS_ROUTE, list_llm_models, methods=["GET"])
    app.add_api_route(API_LLM_MODEL_DETAIL_ROUTE, get_llm_model, methods=["GET"])
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
