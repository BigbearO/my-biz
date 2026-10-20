"""Integration tests for local account management APIs."""

import sqlite3

import httpx
from xianyu import XianyuAccountInfo
from xianyu.protocol import XianyuProtocol
from xianyu_client.core.errors import InvalidXianyuCookieError, XianyuNetworkError

from xianyu_client.app import create_app
from xianyu_client.core.constants import (
    ACCOUNT_COOKIE_MAX_LENGTH,
    ACCOUNT_REQUEST_VALIDATION_ERROR_MESSAGE,
    ACCOUNT_LISTEN_STATE_LISTENING,
    ACCOUNT_TABLE_NAME,
    CREDENTIAL_STATE_READY,
    CREDENTIAL_STATE_UNDECRYPTABLE,
)
from xianyu_client.core.settings import load_settings
from tests.helpers.key_protectors import ReversibleKeyProtector, UnavailableKeyProtector


TEST_LOCAL_TOKEN = "expected-token"
TEST_AUTH_HEADERS = {"X-Local-Token": TEST_LOCAL_TOKEN}
TEST_GOODFISH_ID = "2038890"
TEST_NICKNAME = "buyer-one"
TEST_UPDATED_NICKNAME = "buyer-two"
TEST_COOKIE = f"unb={TEST_GOODFISH_ID}; tracknick={TEST_NICKNAME}; _m_h5_tk=token"
TEST_REPLACEMENT_COOKIE = (
    f"unb={TEST_GOODFISH_ID}; tracknick={TEST_NICKNAME}; _m_h5_tk=replacement-token"
)
TEST_OTHER_ACCOUNT_COOKIE = "unb=404404; tracknick=other-account; _m_h5_tk=other-token"
TEST_REMARK = "primary account"
TEST_UPDATED_REMARK = "updated note"
TEST_CORRUPT_COOKIE = "corrupt-cookie-value"
TEST_COOKIE_OVER_LIMIT_LENGTH = ACCOUNT_COOKIE_MAX_LENGTH + 1
TEST_ACCOUNT_INFO = XianyuAccountInfo(
    goodfish_id=TEST_GOODFISH_ID,
    nickname=TEST_NICKNAME,
)
TEST_OTHER_ACCOUNT_INFO = XianyuAccountInfo(
    goodfish_id="404404",
    nickname="other-account",
)


class StubXianyuProtocol(XianyuProtocol):
    """Deterministic protocol double used without network access."""

    def __init__(
        self,
        result: XianyuAccountInfo | None = None,
        error: Exception | None = None,
    ) -> None:
        """Create a stub with one fixed verification outcome.

        Args:
            result: Account identity to return; defaults to the primary test account.
            error: Optional domain error to raise instead of returning a result.
        """
        self.result = result or TEST_ACCOUNT_INFO
        self.error = error

    async def verify_cookie(self, cookie: str) -> XianyuAccountInfo:
        """Return the configured verification result for one Cookie.

        Args:
            cookie: Cookie plaintext submitted by the API under test.

        Returns:
            The configured account identity.

        Raises:
            Exception: The configured protocol error, when present.
        """
        if self.error is not None:
            raise self.error
        return self.result


def _create_application(
    tmp_path,
    protector_factory=ReversibleKeyProtector,
    protocol: XianyuProtocol | None = None,
):
    """Create an isolated local API application.

    Args:
        tmp_path: Pytest directory used as the application data directory.
        protector_factory: Callable producing a deterministic key protector.
        protocol: Optional protocol double used to isolate Goodfish network calls.

    Returns:
        A FastAPI application and its runtime settings.
    """
    settings = load_settings({
        "GOODFISH_DATA_DIR": str(tmp_path),
        "GOODFISH_LOCAL_TOKEN": TEST_LOCAL_TOKEN,
    })
    return create_app(settings, protector_factory(), protocol or StubXianyuProtocol()), settings


def _create_payload(cookie: str = TEST_COOKIE) -> dict:
    """Build a valid account creation payload.

    Args:
        cookie: Cookie plaintext to submit for encryption.

    Returns:
        A request payload using renderer field names.
    """
    return {
        "cookie": cookie,
        "remark": TEST_REMARK,
    }


def _update_payload(cookie: str | None = None) -> dict:
    """Build an account update payload.

    Args:
        cookie: Optional replacement cookie plaintext.

    Returns:
        A request payload using renderer field names.
    """
    payload = {
        "nickname": TEST_UPDATED_NICKNAME,
        "remark": TEST_UPDATED_REMARK,
    }
    if cookie is not None:
        payload["cookie"] = cookie
    return payload


def _read_account_row(settings) -> sqlite3.Row:
    """Read the sole account row directly from SQLite.

    Args:
        settings: Runtime settings containing the database path.

    Returns:
        The persisted account row including encrypted cookie material.
    """
    connection = sqlite3.connect(settings.database_path)
    connection.row_factory = sqlite3.Row
    try:
        return connection.execute(f"SELECT * FROM {ACCOUNT_TABLE_NAME}").fetchone()
    finally:
        connection.close()


def _update_account_state(settings, column: str, value) -> None:
    """Change one account state column directly for conflict testing.

    Args:
        settings: Runtime settings containing the database path.
        column: State column to update.
        value: Replacement scalar value.
    """
    connection = sqlite3.connect(settings.database_path)
    try:
        connection.execute(f"UPDATE {ACCOUNT_TABLE_NAME} SET {column} = ?", (value,))
        connection.commit()
    finally:
        connection.close()


async def test_account_api_requires_local_token(tmp_path):
    """Account endpoints reject requests without the process-local token."""
    application, _ = _create_application(tmp_path)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            response = await client.get("/api/accounts")

    assert response.status_code == 401


async def test_create_account_encrypts_cookie_and_omits_secret(tmp_path):
    """Account creation persists ciphertext and returns non-secret fields only."""
    application, settings = _create_application(tmp_path)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            response = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            listed = await client.get("/api/accounts", headers=TEST_AUTH_HEADERS)
            decrypted_cookie = await application.state.cookie_credential_manager.decrypt(
                _read_account_row(settings)["cookie"]
            )

    assert response.status_code == 201
    assert response.json() == {
        "id": response.json()["id"],
        "goodfishId": TEST_GOODFISH_ID,
        "nickname": TEST_NICKNAME,
        "isValid": True,
        "listenState": "idle",
        "remark": TEST_REMARK,
        "lastCheckAt": response.json()["lastCheckAt"],
        "createdAt": response.json()["createdAt"],
        "updatedAt": response.json()["updatedAt"],
    }
    assert "cookie" not in response.json()
    assert TEST_COOKIE not in response.text
    assert listed.status_code == 200
    assert TEST_COOKIE not in listed.text
    assert len(listed.json()) == 1
    assert decrypted_cookie == TEST_COOKIE
    assert _read_account_row(settings)["cookie"] != TEST_COOKIE


async def test_create_account_rejects_invalid_cookie_without_persistence(tmp_path):
    """An invalid Goodfish cookie is rejected before any account row is created."""
    protocol = StubXianyuProtocol(error=InvalidXianyuCookieError())
    application, settings = _create_application(tmp_path, protocol=protocol)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            response = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )

    assert response.status_code == 400
    assert TEST_COOKIE not in response.text
    assert _read_account_row(settings) is None


async def test_create_account_network_failure_does_not_create_account(tmp_path):
    """Network failures are reported without treating the Cookie as locally valid."""
    protocol = StubXianyuProtocol(error=XianyuNetworkError())
    application, settings = _create_application(tmp_path, protocol=protocol)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            response = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )

    assert response.status_code == 503
    assert TEST_COOKIE not in response.text
    assert _read_account_row(settings) is None


async def test_create_account_rejects_duplicate_goodfish_id(tmp_path):
    """A second account cannot reuse an existing Goodfish ID."""
    application, _ = _create_application(tmp_path)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            first = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            duplicate = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(TEST_REPLACEMENT_COOKIE),
            )

    assert first.status_code == 201
    assert duplicate.status_code == 409
    assert TEST_COOKIE not in duplicate.text
    assert TEST_REPLACEMENT_COOKIE not in duplicate.text


async def test_validation_failure_does_not_echo_cookie(tmp_path):
    """Schema validation errors return a generic message without request input."""
    application, _ = _create_application(tmp_path)
    transport = httpx.ASGITransport(app=application)
    oversized_cookie = "x" * TEST_COOKIE_OVER_LIMIT_LENGTH

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            response = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(oversized_cookie),
            )

    assert response.status_code == 400
    assert response.json() == {"detail": ACCOUNT_REQUEST_VALIDATION_ERROR_MESSAGE}
    assert oversized_cookie not in response.text


async def test_update_profile_and_replace_cookie(tmp_path):
    """Profile edits preserve ciphertext while explicit cookie edits replace it."""
    application, settings = _create_application(tmp_path)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            created = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            account_id = created.json()["id"]
            profile_updated = await client.put(
                f"/api/accounts/{account_id}",
                headers=TEST_AUTH_HEADERS,
                json=_update_payload(),
            )
            encrypted_cookie_after_profile = _read_account_row(settings)["cookie"]
            cookie_updated = await client.put(
                f"/api/accounts/{account_id}",
                headers=TEST_AUTH_HEADERS,
                json=_update_payload(TEST_REPLACEMENT_COOKIE),
            )
            encrypted_cookie_after_replacement = _read_account_row(settings)["cookie"]
            decrypted_cookie = await application.state.cookie_credential_manager.decrypt(
                encrypted_cookie_after_replacement
            )

    assert profile_updated.status_code == 200
    assert profile_updated.json()["nickname"] == TEST_UPDATED_NICKNAME
    assert profile_updated.json()["remark"] == TEST_UPDATED_REMARK
    assert encrypted_cookie_after_profile != TEST_COOKIE
    assert cookie_updated.status_code == 200
    assert cookie_updated.json()["isValid"] is True
    assert cookie_updated.json()["nickname"] == TEST_NICKNAME
    assert encrypted_cookie_after_replacement != encrypted_cookie_after_profile
    assert decrypted_cookie == TEST_REPLACEMENT_COOKIE
    assert TEST_REPLACEMENT_COOKIE not in cookie_updated.text


async def test_replacement_cookie_must_belong_to_same_goodfish_account(tmp_path):
    """A verified Cookie owned by another account cannot replace this credential."""
    protocol = StubXianyuProtocol(result=TEST_OTHER_ACCOUNT_INFO)
    application, settings = _create_application(tmp_path, protocol=protocol)
    writable_protocol = StubXianyuProtocol()
    writable_application, _ = _create_application(tmp_path, protocol=writable_protocol)
    writable_transport = httpx.ASGITransport(app=writable_application)
    transport = httpx.ASGITransport(app=application)

    async with writable_application.router.lifespan_context(writable_application):
        async with writable_transport, httpx.AsyncClient(
            transport=writable_transport,
            base_url="http://127.0.0.1"
        ) as client:
            created = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            encrypted_cookie_before_replacement = _read_account_row(settings)["cookie"]

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            rejected = await client.put(
                f"/api/accounts/{created.json()['id']}",
                headers=TEST_AUTH_HEADERS,
                json=_update_payload(TEST_OTHER_ACCOUNT_COOKIE),
            )

    assert rejected.status_code == 409
    assert TEST_OTHER_ACCOUNT_COOKIE not in rejected.text
    assert _read_account_row(settings)["cookie"] == encrypted_cookie_before_replacement


async def test_manual_verify_success_updates_validation_state(tmp_path):
    """Manual verification marks an idle account valid without changing its cookie."""
    application, settings = _create_application(tmp_path)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            created = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            encrypted_cookie = _read_account_row(settings)["cookie"]
            _update_account_state(settings, "is_valid", 0)
            verified = await client.post(
                f"/api/accounts/{created.json()['id']}/verify",
                headers=TEST_AUTH_HEADERS,
            )

    assert verified.status_code == 200
    assert verified.json()["isValid"] is True
    assert _read_account_row(settings)["is_valid"] == 1
    assert _read_account_row(settings)["cookie"] == encrypted_cookie
    assert TEST_COOKIE not in verified.text


async def test_manual_verify_invalid_cookie_preserves_encrypted_cookie(tmp_path):
    """Expired login state is recorded without deleting or replacing local material."""
    protocol = StubXianyuProtocol()
    application, settings = _create_application(tmp_path, protocol=protocol)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            created = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            encrypted_cookie = _read_account_row(settings)["cookie"]
            protocol.error = InvalidXianyuCookieError()
            rejected = await client.post(
                f"/api/accounts/{created.json()['id']}/verify",
                headers=TEST_AUTH_HEADERS,
            )

    assert rejected.status_code == 400
    row = _read_account_row(settings)
    assert row["is_valid"] == 0
    assert row["cookie"] == encrypted_cookie
    assert TEST_COOKIE not in rejected.text


async def test_manual_verify_network_failure_keeps_previous_state(tmp_path):
    """Transport failures do not incorrectly mark a locally valid cookie invalid."""
    protocol = StubXianyuProtocol()
    application, settings = _create_application(tmp_path, protocol=protocol)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            created = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            row_before_failure = _read_account_row(settings)
            protocol.error = XianyuNetworkError()
            failed = await client.post(
                f"/api/accounts/{created.json()['id']}/verify",
                headers=TEST_AUTH_HEADERS,
            )
            row_after_failure = _read_account_row(settings)

    assert failed.status_code == 503
    assert row_after_failure["is_valid"] == row_before_failure["is_valid"]
    assert row_after_failure["last_check_at"] == row_before_failure["last_check_at"]
    assert TEST_COOKIE not in failed.text


async def test_missing_account_returns_not_found(tmp_path):
    """Updates and deletes use a stable not-found response."""
    application, _ = _create_application(tmp_path)
    transport = httpx.ASGITransport(app=application)
    missing_account_id = 1

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            update = await client.put(
                f"/api/accounts/{missing_account_id}",
                headers=TEST_AUTH_HEADERS,
                json=_update_payload(),
            )
            delete = await client.delete(
                f"/api/accounts/{missing_account_id}",
                headers=TEST_AUTH_HEADERS,
            )

    assert update.status_code == 404
    assert delete.status_code == 404


async def test_delete_account_removes_local_row(tmp_path):
    """Deleting an idle account removes its ciphertext and refreshes state."""
    application, settings = _create_application(tmp_path)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            created = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            deleted = await client.delete(
                f"/api/accounts/{created.json()['id']}",
                headers=TEST_AUTH_HEADERS,
            )
            listed = await client.get("/api/accounts", headers=TEST_AUTH_HEADERS)

    assert deleted.status_code == 200
    assert deleted.json()["goodfishId"] == TEST_GOODFISH_ID
    assert listed.status_code == 200
    assert listed.json() == []
    assert _read_account_row(settings) is None


async def test_corrupt_cookie_can_be_reentered_with_valid_master_key(tmp_path):
    """A decryptable master key allows recovery from damaged cookie ciphertext."""
    application, settings = _create_application(tmp_path)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            created = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            _update_account_state(settings, "cookie", TEST_CORRUPT_COOKIE)
            await application.state.credential_service.initialize()
            failed_status = await client.get("/api/system/status", headers=TEST_AUTH_HEADERS)
            recovered = await client.put(
                f"/api/accounts/{created.json()['id']}",
                headers=TEST_AUTH_HEADERS,
                json=_update_payload(TEST_REPLACEMENT_COOKIE),
            )
            recovered_status = await client.get("/api/system/status", headers=TEST_AUTH_HEADERS)

    assert failed_status.json()["credentialState"] == CREDENTIAL_STATE_UNDECRYPTABLE
    assert failed_status.json()["cookieWritable"] is True
    assert recovered.status_code == 200
    assert recovered_status.json()["credentialState"] == CREDENTIAL_STATE_READY
    assert recovered_status.json()["cookieWritable"] is True


async def test_unavailable_master_key_blocks_cookie_writes_only(tmp_path):
    """Profile management remains available when the master key cannot be restored."""
    writable_application, writable_settings = _create_application(tmp_path)
    writable_transport = httpx.ASGITransport(app=writable_application)

    async with writable_application.router.lifespan_context(writable_application):
        async with writable_transport, httpx.AsyncClient(
            transport=writable_transport,
            base_url="http://127.0.0.1"
        ) as client:
            created = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            writable_account_id = created.json()["id"]

    application, _ = _create_application(tmp_path, UnavailableKeyProtector)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            listed = await client.get("/api/accounts", headers=TEST_AUTH_HEADERS)
            status = await client.get("/api/system/status", headers=TEST_AUTH_HEADERS)
            profile_updated = await client.put(
                f"/api/accounts/{writable_account_id}",
                headers=TEST_AUTH_HEADERS,
                json=_update_payload(),
            )
            cookie_update = await client.put(
                f"/api/accounts/{writable_account_id}",
                headers=TEST_AUTH_HEADERS,
                json=_update_payload(TEST_REPLACEMENT_COOKIE),
            )

    assert listed.status_code == 200
    assert len(listed.json()) == 1
    assert status.json()["credentialState"] == CREDENTIAL_STATE_UNDECRYPTABLE
    assert status.json()["cookieWritable"] is False
    assert profile_updated.status_code == 200
    assert cookie_update.status_code == 503
    assert TEST_REPLACEMENT_COOKIE not in cookie_update.text
    assert _read_account_row(writable_settings)["cookie"] != TEST_COOKIE


async def test_listening_account_rejects_cookie_replacement_and_deletion(tmp_path):
    """Non-idle accounts cannot have credentials changed or rows deleted."""
    application, settings = _create_application(tmp_path)
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            created = await client.post(
                "/api/accounts",
                headers=TEST_AUTH_HEADERS,
                json=_create_payload(),
            )
            account_id = created.json()["id"]
            _update_account_state(settings, "listen_state", ACCOUNT_LISTEN_STATE_LISTENING)
            profile_updated = await client.put(
                f"/api/accounts/{account_id}",
                headers=TEST_AUTH_HEADERS,
                json=_update_payload(),
            )
            cookie_update = await client.put(
                f"/api/accounts/{account_id}",
                headers=TEST_AUTH_HEADERS,
                json=_update_payload(TEST_REPLACEMENT_COOKIE),
            )
            delete = await client.delete(
                f"/api/accounts/{account_id}",
                headers=TEST_AUTH_HEADERS,
            )

    assert profile_updated.status_code == 200
    assert cookie_update.status_code == 409
    assert delete.status_code == 409
