"""FastAPI application factory for the local desktop service."""

from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from xianyu_client.api.system_api import router as system_router
from xianyu_client.core.constants import FILE_ORIGIN, VITE_DEV_ORIGIN
from xianyu_client.core.settings import RuntimeSettings
from xianyu_client.core.constants import MASTER_KEY_FILE_NAME
from xianyu_client.db.migrations import run_migrations
from xianyu_client.db.session import create_database_engine, create_session_maker
from xianyu_client.managers.account_manager import AccountManager
from xianyu_client.managers.config_manager import ConfigManager
from xianyu_client.managers.cookie_credential_manager import CookieCredentialManager
from xianyu_client.managers.master_key_manager import MasterKeyManager
from xianyu_client.services.credential_service import CredentialService
from xianyu_client.services.system_service import SystemService


def create_app(settings: RuntimeSettings, master_key_protector=None) -> FastAPI:
    """Create the configured local API application.

    Args:
        settings: Runtime settings supplied by the Electron process.
        master_key_protector: Optional key protector injection point used by tests.

    Returns:
        A FastAPI application with database lifecycle management attached.
    """
    database_engine = create_database_engine(settings.database_path)
    session_factory = create_session_maker(database_engine)
    config_manager = ConfigManager(session_factory)
    account_manager = AccountManager(session_factory)
    credential_manager = CookieCredentialManager(
        MasterKeyManager(settings.data_dir / MASTER_KEY_FILE_NAME, master_key_protector)
    )
    credential_service = CredentialService(credential_manager, account_manager)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        """Initialize schema and defaults, then dispose database resources."""
        await run_database_lifecycle(Path(settings.database_path))
        await config_manager.initialize_defaults()
        await credential_service.initialize()
        yield
        await database_engine.dispose()

    app = FastAPI(lifespan=lifespan)
    app.state.settings = settings
    app.state.database_engine = database_engine
    app.state.config_manager = config_manager
    app.state.cookie_credential_manager = credential_manager
    app.state.credential_service = credential_service
    app.state.system_service = SystemService(config_manager, credential_service)
    app.include_router(system_router)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=[VITE_DEV_ORIGIN, FILE_ORIGIN],
        allow_credentials=False,
        allow_methods=["GET"],
        allow_headers=["Content-Type", "X-Local-Token"],
    )
    return app


async def run_database_lifecycle(database_path: Path) -> None:
    """Run schema migrations off the event loop before sessions are used."""
    import asyncio

    await asyncio.to_thread(run_migrations, database_path)

