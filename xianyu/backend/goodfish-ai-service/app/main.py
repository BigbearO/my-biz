import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI

from .api.health import health
from .bootstrap import BootstrapSettings
from .constants import SERVICE_VERSION, UVICORN_WORKERS_SINGLE
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
                )

                async def on_config_changed(
                    tenant: str,
                    group: str,
                    data_id: str,
                    content: str,
                ) -> None:
                    config_store.apply_remote(content, effective_bootstrap.nacos_config_format)

                await nacos.watch_config(on_config_changed)
            elif not config_store.config:
                raise ConfigEmptyError(
                    "No effective configuration: local config is empty and NACOS_DATA_ID is empty"
                )

            await nacos.register_http_instance()
            app.state.bootstrap = effective_bootstrap
            app.state.runtime_config_store = config_store
            app.state.service_version = SERVICE_VERSION
            logger.info("Service startup completed")
            yield
        finally:
            await nacos.deregister_all()
            await nacos.shutdown()
            logger.info("Service shutdown completed")

    app = FastAPI(lifespan=lifespan)
    app.add_api_route("/health", health, methods=["GET"])
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
