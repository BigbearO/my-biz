"""FastAPI application factory for the local desktop service."""

from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from loguru import logger

from xianyu_client.api.account_api import router as account_router
from xianyu_client.api.item_api import router as item_router
from xianyu_client.api.system_api import router as system_router
from xianyu.http_protocol import HttpXianyuProtocol
from xianyu_client.core.constants import (
    ACCOUNT_REQUEST_VALIDATION_ERROR_MESSAGE,
    AUTH_STARTUP_CHECK_FAILED_LOG_MESSAGE,
    FILE_ORIGIN,
    LOCAL_API_ALLOWED_METHODS,
    LOCAL_API_INTERNAL_ERROR_MESSAGE,
    LOCAL_API_UNEXPECTED_ERROR_LOG_MESSAGE,
    LOCAL_TOKEN_HEADER,
    VITE_DEV_ORIGIN,
)
from xianyu_client.core.settings import RuntimeSettings
from xianyu_client.core.constants import MASTER_KEY_FILE_NAME
from xianyu_client.db.migrations import run_migrations
from xianyu_client.db.session import create_database_engine, create_session_maker
from xianyu_client.managers.account_manager import AccountManager
from xianyu_client.managers.auth_token_manager import AuthTokenManager
from xianyu_client.managers.config_manager import ConfigManager
from xianyu_client.managers.cookie_credential_manager import CookieCredentialManager
from xianyu_client.managers.master_key_manager import MasterKeyManager
from xianyu_client.services.auth_service import AuthService
from xianyu_client.services.cloud_client import CloudClient
from xianyu_client.services.credential_service import CredentialService
from xianyu_client.services.item_sync_service import ItemSyncService
from xianyu_client.services.system_service import SystemService
from xianyu_client.services.account_service import AccountService


def create_app(settings: RuntimeSettings, master_key_protector=None, xianyu_protocol=None) -> FastAPI:
    """Create the configured local API application.

    Args:
        settings: Runtime settings supplied by the Electron process.
        master_key_protector: Optional key protector injection point used by tests.
        xianyu_protocol: Optional protocol implementation injection point used by tests.

    Returns:
        A FastAPI application with database lifecycle management attached.
    """
    database_engine = create_database_engine(settings.database_path)
    session_factory = create_session_maker(database_engine)
    config_manager = ConfigManager(session_factory)
    account_manager = AccountManager(session_factory)
    master_key_manager = MasterKeyManager(
        settings.data_dir / MASTER_KEY_FILE_NAME, master_key_protector
    )
    credential_manager = CookieCredentialManager(master_key_manager)
    credential_service = CredentialService(credential_manager, account_manager)
    auth_service = AuthService(AuthTokenManager(master_key_manager, config_manager))
    cloud_client = CloudClient(settings.cloud_base_url, auth_service.get_credentials)
    auth_service.attach_cloud_client(cloud_client)
    protocol = xianyu_protocol or HttpXianyuProtocol()
    account_service = AccountService(
        account_manager,
        credential_manager,
        credential_service,
        protocol,
        auth_service,
        cloud_client,
    )
    item_sync_service = ItemSyncService(account_service, protocol, cloud_client)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        """Initialize schema, restore login state, then dispose resources."""
        await run_database_lifecycle(Path(settings.database_path))
        await config_manager.initialize_defaults()
        await credential_service.initialize()
        try:
            await auth_service.load_persisted_state()
        except Exception:
            logger.exception(AUTH_STARTUP_CHECK_FAILED_LOG_MESSAGE)
        await auth_service.start_periodic_check()
        try:
            yield
        finally:
            await auth_service.stop_periodic_check()
            await item_sync_service.stop()
            await database_engine.dispose()

    app = FastAPI(lifespan=lifespan)
    app.state.settings = settings
    app.state.database_engine = database_engine
    app.state.config_manager = config_manager
    app.state.cookie_credential_manager = credential_manager
    app.state.credential_service = credential_service
    app.state.auth_service = auth_service
    app.state.cloud_client = cloud_client
    app.state.account_service = account_service
    app.state.item_sync_service = item_sync_service
    app.state.system_service = SystemService(config_manager, credential_service, auth_service)
    app.include_router(account_router)
    app.include_router(item_router)
    app.include_router(system_router)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=[VITE_DEV_ORIGIN, FILE_ORIGIN],
        allow_credentials=False,
        allow_methods=LOCAL_API_ALLOWED_METHODS,
        allow_headers=["Content-Type", LOCAL_TOKEN_HEADER],
    )
    app.add_exception_handler(RequestValidationError, sanitize_request_validation_error)
    app.add_exception_handler(Exception, handle_unexpected_error)
    return app


async def handle_unexpected_error(request: Request, exception: Exception) -> JSONResponse:
    """Log an unexpected local API failure without echoing request content.

    Args:
        request: Failed request used only for its path.
        exception: Unexpected error raised by a route or dependency.

    Returns:
        A JSON response containing only a stable non-secret message.
    """
    logger.opt(exception=exception).error(
        LOCAL_API_UNEXPECTED_ERROR_LOG_MESSAGE,
        request.url.path,
    )
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": LOCAL_API_INTERNAL_ERROR_MESSAGE},
    )


async def sanitize_request_validation_error(
    _request,
    exception: RequestValidationError,
) -> JSONResponse:
    """Return a generic account validation error without echoing request input.

    Args:
        _request: Unused request whose body failed schema validation.
        exception: FastAPI validation error that may contain submitted values.

    Returns:
        A JSON response containing only a stable non-secret message.
    """
    return JSONResponse(
        status_code=400,
        content={"detail": ACCOUNT_REQUEST_VALIDATION_ERROR_MESSAGE},
    )


async def run_database_lifecycle(database_path: Path) -> None:
    """Run schema migrations off the event loop before sessions are used."""
    import asyncio

    await asyncio.to_thread(run_migrations, database_path)

