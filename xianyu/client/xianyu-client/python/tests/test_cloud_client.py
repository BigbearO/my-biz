"""Tests for the cloud boundary used by the local service."""

import pytest

from xianyu_client.core.constants import (
    CLOUD_HTTP_STATUS_FORBIDDEN,
    CLOUD_HTTP_STATUS_SERVER_ERROR_MIN,
    CLOUD_HTTP_STATUS_UNAUTHORIZED,
    CLOUD_ITEM_SYNC_INSERTED_KEY,
    CLOUD_ITEM_SYNC_INSERTED_LEGACY_KEY,
    CLOUD_ITEM_SYNC_UNCHANGED_KEY,
    CLOUD_ITEM_SYNC_UPDATED_KEY,
    CLOUD_ITEM_SYNC_UPDATED_LEGACY_KEY,
    CLOUD_SUCCESS_CODE,
    CLOUD_SUCCESS_HTTP_STATUS,
)
from xianyu_client.core.errors import (
    CloudForbiddenError,
    CloudReportError,
    CloudUnauthorizedError,
    CloudUnavailableError,
)
from xianyu_client.services import cloud_client as cloud_client_module
from xianyu_client.services.cloud_client import CloudClient

TEST_BASE_URL = "http://127.0.0.1:8080"
TEST_TOKEN = "test-token"
TEST_CLIENT_ID = "test-client"
TEST_GOODFISH_ID = "goodfish-1"
TEST_NICKNAME = "nickname"
TEST_REMARK = "remark"
TEST_INSERTED_COUNT = 2
TEST_UPDATED_COUNT = 1
TEST_UNCHANGED_COUNT = 3
DATA_KEY = "data"
CODE_KEY = "code"
CLOUD_FAILURE_CODE = 500


def build_client() -> CloudClient:
    """Create a cloud client bound to a fixed token pair."""

    async def token_provider() -> tuple[str, str]:
        return TEST_TOKEN, TEST_CLIENT_ID

    return CloudClient(TEST_BASE_URL, token_provider)


def patch_send(monkeypatch, status: int, body: dict) -> list[tuple]:
    """Replace the HTTP round trip and record every call."""
    calls: list[tuple] = []

    async def fake_send(_self, method, url, headers, payload):
        calls.append((method, url, headers, payload))
        return status, body

    monkeypatch.setattr(CloudClient, "_send", fake_send)
    return calls


async def test_check_access_returns_true_when_cloud_allows(monkeypatch):
    """A 200 response with data=true keeps the client authorized."""
    patch_send(monkeypatch, CLOUD_SUCCESS_HTTP_STATUS, {DATA_KEY: True})

    assert await build_client().check_access() is True


async def test_check_access_returns_false_when_cloud_denies(monkeypatch):
    """A 200 response with data=false marks the authorization as expired."""
    patch_send(monkeypatch, CLOUD_SUCCESS_HTTP_STATUS, {DATA_KEY: False})

    assert await build_client().check_access() is False


async def test_check_access_maps_unauthorized(monkeypatch):
    """A 401 response surfaces as an unauthorized error."""
    patch_send(monkeypatch, CLOUD_HTTP_STATUS_UNAUTHORIZED, {})

    with pytest.raises(CloudUnauthorizedError):
        await build_client().check_access()


async def test_check_access_maps_forbidden(monkeypatch):
    """A 403 response surfaces as a forbidden error."""
    patch_send(monkeypatch, CLOUD_HTTP_STATUS_FORBIDDEN, {})

    with pytest.raises(CloudForbiddenError):
        await build_client().check_access()


async def test_check_access_maps_server_error(monkeypatch):
    """A 5xx response surfaces as an unavailable error after retries."""
    monkeypatch.setattr(cloud_client_module, "CLOUD_RETRY_LIMIT", 0)
    patch_send(monkeypatch, CLOUD_HTTP_STATUS_SERVER_ERROR_MIN, {})

    with pytest.raises(CloudUnavailableError):
        await build_client().check_access()


async def test_report_account_accepts_success_code(monkeypatch):
    """A 200 response with the business success code completes the report."""
    calls = patch_send(monkeypatch, CLOUD_SUCCESS_HTTP_STATUS, {CODE_KEY: CLOUD_SUCCESS_CODE})

    await build_client().report_account(TEST_GOODFISH_ID, TEST_NICKNAME, TEST_REMARK)

    assert len(calls) == 1


async def test_report_account_maps_unauthorized_body_code(monkeypatch):
    """The gateway rejects a stale token with HTTP 200 and body code 401."""
    patch_send(monkeypatch, CLOUD_SUCCESS_HTTP_STATUS, {CODE_KEY: CLOUD_HTTP_STATUS_UNAUTHORIZED})

    with pytest.raises(CloudUnauthorizedError):
        await build_client().report_account(TEST_GOODFISH_ID, TEST_NICKNAME, TEST_REMARK)


async def test_report_account_maps_forbidden_body_code(monkeypatch):
    """The gateway reports an expired authorization with HTTP 200 and body code 403."""
    patch_send(monkeypatch, CLOUD_SUCCESS_HTTP_STATUS, {CODE_KEY: CLOUD_HTTP_STATUS_FORBIDDEN})

    with pytest.raises(CloudForbiddenError):
        await build_client().report_account(TEST_GOODFISH_ID, TEST_NICKNAME, TEST_REMARK)


async def test_report_account_maps_business_failure_to_report_error(monkeypatch):
    """A non-auth business failure still surfaces as a report error."""
    patch_send(monkeypatch, CLOUD_SUCCESS_HTTP_STATUS, {CODE_KEY: CLOUD_FAILURE_CODE})

    with pytest.raises(CloudReportError):
        await build_client().report_account(TEST_GOODFISH_ID, TEST_NICKNAME, TEST_REMARK)


async def test_sync_items_returns_cloud_counters(monkeypatch):
    """A 200 response with counters is surfaced as inserted/updated/unchanged."""
    patch_send(
        monkeypatch,
        CLOUD_SUCCESS_HTTP_STATUS,
        {
            CODE_KEY: CLOUD_SUCCESS_CODE,
            DATA_KEY: {
                CLOUD_ITEM_SYNC_INSERTED_KEY: TEST_INSERTED_COUNT,
                CLOUD_ITEM_SYNC_UPDATED_KEY: TEST_UPDATED_COUNT,
                CLOUD_ITEM_SYNC_UNCHANGED_KEY: TEST_UNCHANGED_COUNT,
            },
        },
    )

    result = await build_client().sync_items(TEST_GOODFISH_ID, [])

    assert result == (TEST_INSERTED_COUNT, TEST_UPDATED_COUNT, TEST_UNCHANGED_COUNT)


async def test_sync_items_reads_legacy_counter_field_names(monkeypatch):
    """Older gateways report `insertedCount`; the client still understands them."""
    patch_send(
        monkeypatch,
        CLOUD_SUCCESS_HTTP_STATUS,
        {
            CODE_KEY: CLOUD_SUCCESS_CODE,
            DATA_KEY: {
                CLOUD_ITEM_SYNC_INSERTED_LEGACY_KEY: TEST_INSERTED_COUNT,
                CLOUD_ITEM_SYNC_UPDATED_LEGACY_KEY: TEST_UPDATED_COUNT,
            },
        },
    )

    result = await build_client().sync_items(TEST_GOODFISH_ID, [])

    assert result == (TEST_INSERTED_COUNT, TEST_UPDATED_COUNT, 0)
