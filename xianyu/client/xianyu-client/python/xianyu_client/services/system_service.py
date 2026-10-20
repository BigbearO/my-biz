"""Business orchestration for local system status."""

from xianyu_client import APP_VERSION
from xianyu_client.core.constants import (
    AUTH_STATE_NOT_LOGGED_IN,
    AUTH_STATE_LOGGED_IN,
    CLOUD_STATE_NOT_CONFIGURED,
    CONFIG_AUTH_TOKEN,
    CONFIG_EMPTY_VALUE,
    CREDENTIAL_STATE_READY,
    INITIAL_LISTEN_COUNT,
    SERVICE_STATE_READY,
)
from xianyu_client.managers.config_manager import ConfigManager
from xianyu_client.services.credential_service import CredentialService
from xianyu_client.schemas.system_schemas import SystemStatusResponse


class SystemService:
    """Assembles local service status from configuration and runtime state."""

    def __init__(self, config_manager: ConfigManager, credential_service: CredentialService) -> None:
        """Create a system service with configuration and credential state sources.

        Args:
            config_manager: Manager for local runtime configuration.
            credential_service: Service exposing credential decryptability state.
        """
        self._config_manager = config_manager
        self._credential_service = credential_service

    async def get_status(self) -> SystemStatusResponse:
        """Return the local service status without exposing secrets.

        Returns:
            Current runtime, configuration, authentication, and credential states.
        """
        auth_token = await self._config_manager.get_value(CONFIG_AUTH_TOKEN, CONFIG_EMPTY_VALUE)
        return SystemStatusResponse(
            version=APP_VERSION,
            serviceState=SERVICE_STATE_READY,
            listenCount=INITIAL_LISTEN_COUNT,
            cloudState=CLOUD_STATE_NOT_CONFIGURED,
            authState=AUTH_STATE_NOT_LOGGED_IN if auth_token == CONFIG_EMPTY_VALUE else AUTH_STATE_LOGGED_IN,
            credentialState=self._credential_service.state or CREDENTIAL_STATE_READY,
            cookieWritable=self._credential_service.cookie_writable,
        )

