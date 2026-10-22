"""Business orchestration for local system status."""

from xianyu_client import APP_VERSION
from xianyu_client.core.constants import (
    AUTH_STATE_NOT_LOGGED_IN,
    AUTH_STATE_UNAVAILABLE,
    CLOUD_STATE_NOT_CONFIGURED,
    CLOUD_STATE_ONLINE,
    CLOUD_STATE_UNAVAILABLE,
    CREDENTIAL_STATE_READY,
    INITIAL_LISTEN_COUNT,
    SERVICE_STATE_READY,
)
from xianyu_client.core.errors import AuthTokenUnavailableError
from xianyu_client.managers.config_manager import ConfigManager
from xianyu_client.schemas.system_schemas import SystemStatusResponse
from xianyu_client.services.auth_service import AuthService
from xianyu_client.services.credential_service import CredentialService


class SystemService:
    """Assembles local service status from configuration and runtime state."""

    def __init__(
        self,
        config_manager: ConfigManager,
        credential_service: CredentialService,
        auth_service: AuthService,
    ) -> None:
        """Create a system service with configuration and runtime state sources.

        Args:
            config_manager: Manager for local runtime configuration.
            credential_service: Service exposing credential decryptability state.
            auth_service: Service exposing login and authorization state.
        """
        self._config_manager = config_manager
        self._credential_service = credential_service
        self._auth_service = auth_service

    async def get_status(self) -> SystemStatusResponse:
        """Return the local service status without exposing secrets.

        Returns:
            Current runtime, configuration, authentication, and credential states.
        """
        auth_state, cloud_state = await self._resolve_auth_state()
        return SystemStatusResponse(
            version=APP_VERSION,
            serviceState=SERVICE_STATE_READY,
            listenCount=INITIAL_LISTEN_COUNT,
            cloudState=cloud_state,
            authState=auth_state,
            credentialState=self._credential_service.state or CREDENTIAL_STATE_READY,
            cookieWritable=self._credential_service.cookie_writable,
        )

    async def _resolve_auth_state(self) -> tuple[str, str]:
        """Resolve the auth and cloud states from the stored token pair.

        Returns:
            A `(auth_state, cloud_state)` tuple for the status response.
        """
        try:
            token, _client_id = await self._auth_service.get_credentials()
        except AuthTokenUnavailableError:
            return AUTH_STATE_NOT_LOGGED_IN, CLOUD_STATE_NOT_CONFIGURED
        if not token:
            return AUTH_STATE_NOT_LOGGED_IN, CLOUD_STATE_NOT_CONFIGURED
        auth_state = self._auth_service.state
        cloud_state = CLOUD_STATE_UNAVAILABLE if auth_state == AUTH_STATE_UNAVAILABLE else CLOUD_STATE_ONLINE
        return auth_state, cloud_state
