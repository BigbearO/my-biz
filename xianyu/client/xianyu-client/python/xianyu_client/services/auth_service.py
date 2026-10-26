"""Login state and authorization checks for the local service."""

import asyncio
from typing import Optional

from loguru import logger

from xianyu_client.core.constants import (
    AUTH_ACCESS_CHECK_FAILED_LOG_MESSAGE,
    AUTH_LOGIN_RESULT_LOG_MESSAGE,
    AUTH_STATE_EXPIRED,
    AUTH_STATE_FROZEN,
    AUTH_STATE_LOGGED_IN,
    AUTH_STATE_NOT_LOGGED_IN,
    AUTH_STATE_UNAVAILABLE,
    CLOUD_AUTH_CHECK_INTERVAL_SECONDS,
)
from xianyu_client.core.errors import (
    AuthTokenUnavailableError,
    CloudForbiddenError,
    CloudUnauthorizedError,
    CloudUnavailableError,
    NotLoggedInError,
)
from xianyu_client.managers.auth_token_manager import AuthTokenManager
from xianyu_client.services.cloud_client import CloudClient


class AuthService:
    """Owns the login token pair and the authorization state machine."""

    def __init__(self, token_manager: AuthTokenManager) -> None:
        """Create an auth service with encrypted token storage.

        Args:
            token_manager: Manager persisting the encrypted token pair.
        """
        self._token_manager = token_manager
        self._cloud_client: Optional[CloudClient] = None
        self._state = AUTH_STATE_NOT_LOGGED_IN
        self._periodic_task: Optional[asyncio.Task] = None

    @property
    def state(self) -> str:
        """Return the current authorization state."""
        return self._state

    def attach_cloud_client(self, cloud_client: CloudClient) -> None:
        """Attach the outbound client used for authorization checks.

        Args:
            cloud_client: Client bound to the same token provider.
        """
        self._cloud_client = cloud_client

    async def get_credentials(self) -> tuple[str, str]:
        """Return the stored token pair for outbound requests.

        Returns:
            A `(token, client_id)` tuple; both empty when logged out.
        """
        return await self._token_manager.load()

    async def ensure_logged_in(self) -> None:
        """Reject outbound calls when no usable token exists.

        Raises:
            NotLoggedInError: When no token is stored.
            CloudForbiddenError: When the application is frozen.
        """
        if self._state == AUTH_STATE_FROZEN:
            raise CloudForbiddenError()
        token, _client_id = await self.get_credentials()
        if not token:
            raise NotLoggedInError()

    async def login(self, token: str, client_id: str) -> str:
        """Persist a freshly issued token pair and re-check authorization.

        Args:
            token: Access token returned by the management backend.
            client_id: Client identifier paired with the token.

        Returns:
            The authorization state after the check.
        """
        await self._token_manager.save(token, client_id)
        self._state = AUTH_STATE_LOGGED_IN
        auth_state = await self.refresh_access()
        logger.info(AUTH_LOGIN_RESULT_LOG_MESSAGE, auth_state)
        return auth_state

    async def logout(self) -> str:
        """Remove the stored token pair.

        Returns:
            The resulting authorization state.
        """
        await self._token_manager.clear()
        self._state = AUTH_STATE_NOT_LOGGED_IN
        return self._state

    async def refresh_access(self) -> str:
        """Call the cloud authorization endpoint and update local state.

        Returns:
            The updated authorization state.
        """
        if self._cloud_client is None:
            return self._state
        token, _client_id = await self.get_credentials()
        if not token:
            self._state = AUTH_STATE_NOT_LOGGED_IN
            return self._state
        try:
            allowed = await self._cloud_client.check_access()
        except NotLoggedInError:
            self._state = AUTH_STATE_NOT_LOGGED_IN
        except CloudUnauthorizedError:
            await self._token_manager.clear()
            self._state = AUTH_STATE_NOT_LOGGED_IN
        except CloudForbiddenError:
            await self._token_manager.clear()
            self._state = AUTH_STATE_FROZEN
        except CloudUnavailableError:
            self._state = AUTH_STATE_UNAVAILABLE
        except Exception as error:
            logger.opt(exception=error).error(AUTH_ACCESS_CHECK_FAILED_LOG_MESSAGE)
            self._state = AUTH_STATE_UNAVAILABLE
        else:
            if allowed:
                self._state = AUTH_STATE_LOGGED_IN
            else:
                await self._token_manager.clear()
                self._state = AUTH_STATE_EXPIRED
        return self._state

    async def load_persisted_token(self) -> bool:
        """Restore the login state from disk without any network access.

        Startup must stay responsive, so the first authorization check is left
        to a background task started by the application lifespan.

        Returns:
            True when a usable token exists and a background check is warranted.
        """
        try:
            token, _client_id = await self.get_credentials()
        except AuthTokenUnavailableError:
            self._state = AUTH_STATE_NOT_LOGGED_IN
            return False
        self._state = AUTH_STATE_LOGGED_IN if token else AUTH_STATE_NOT_LOGGED_IN
        return bool(token)

    async def start_periodic_check(self) -> None:
        """Run the hourly authorization check in the background."""
        if self._periodic_task is not None:
            return
        self._periodic_task = asyncio.create_task(self._run_periodic_check())

    async def stop_periodic_check(self) -> None:
        """Cancel the background authorization check."""
        if self._periodic_task is None:
            return
        self._periodic_task.cancel()
        try:
            await self._periodic_task
        except asyncio.CancelledError:
            pass
        self._periodic_task = None

    async def _run_periodic_check(self) -> None:
        """Refresh authorization once per configured interval."""
        while True:
            await asyncio.sleep(CLOUD_AUTH_CHECK_INTERVAL_SECONDS)
            try:
                await self.refresh_access()
            except Exception:
                logger.exception("Periodic authorization check failed")
