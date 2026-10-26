"""Tests for product synchronisation parsing and orchestration."""

import asyncio

from xianyu.http_protocol import _extract_items
from xianyu.protocol import XianyuItem
from xianyu_client.core.constants import (
    ITEM_SYNC_EMPTY_RESULT_MESSAGE,
    ITEM_SYNC_NETWORK_FAILURE_MESSAGE,
    ITEM_SYNC_STATE_FAILED,
    ITEM_SYNC_STATE_RUNNING,
    ITEM_SYNC_STATE_SUCCESS,
)
from xianyu_client.core.errors import XianyuNetworkError
from xianyu_client.schemas.account_schemas import AccountDetailResponse
from xianyu_client.services.item_sync_service import ItemSyncService, _safe_price, _to_payload

TEST_GOODFISH_ID = "2038890"
TEST_COOKIE = "unb=2038890; _m_h5_tk=token_1"
TEST_ACCOUNT_ID = 1
TEST_POLL_INTERVAL_SECONDS = 0.01
TEST_POLL_LIMIT = 100
TEST_PRICE = "199.90"
TEST_INVALID_PRICE = "¥199"


def _detail() -> AccountDetailResponse:
    """Build the minimal account detail consumed by the sync service."""
    return AccountDetailResponse(
        id=TEST_ACCOUNT_ID,
        goodfish_id=TEST_GOODFISH_ID,
        goodfish_nickname="买家",
        is_valid=True,
        listen_state="idle",
        remark="",
        last_check_at="2026-01-01T00:00:00",
        created_at="2026-01-01T00:00:00",
        updated_at="2026-01-01T00:00:00",
        cookie=TEST_COOKIE,
    )


def _item(item_id: str) -> XianyuItem:
    """Build one listing item used by the orchestration tests."""
    return XianyuItem(
        goodfish_item_id=item_id,
        title=f"标题{item_id}",
        description="",
        price=TEST_PRICE,
        stock_status="0",
    )


class _FakeAccountService:
    """Account boundary returning one fixed detail."""

    async def get_account_detail(self, account_id: int) -> AccountDetailResponse:
        """Return the fixed detail regardless of the requested identifier."""
        return _detail()


class _FakeProtocol:
    """Protocol boundary returning a fixed listing or raising one error."""

    def __init__(self, items: list[XianyuItem], error: Exception | None = None) -> None:
        """Store the listing and the optional failure."""
        self._items = items
        self._error = error

    async def fetch_items(self, cookie: str) -> list[XianyuItem]:
        """Return the fixed listing or raise the configured error."""
        if self._error is not None:
            raise self._error
        return self._items


class _FakeCloudClient:
    """Cloud boundary capturing the uploaded payload."""

    def __init__(self, inserted: int, updated: int) -> None:
        """Store the counters reported back to the service."""
        self._inserted = inserted
        self._updated = updated
        self.uploaded_goodfish_id: str | None = None
        self.uploaded_items: list[dict] = []

    async def sync_items(self, goodfish_id: str, items: list[dict]) -> tuple[int, int]:
        """Capture the upload and return the fixed counters."""
        self.uploaded_goodfish_id = goodfish_id
        self.uploaded_items = items
        return self._inserted, self._updated


async def _wait_for_finish(service: ItemSyncService):
    """Poll the service until the background synchronisation leaves running."""
    for _ in range(TEST_POLL_LIMIT):
        status = await service.get_status(TEST_ACCOUNT_ID)
        if status.state != ITEM_SYNC_STATE_RUNNING:
            return status
        await asyncio.sleep(TEST_POLL_INTERVAL_SECONDS)
    raise AssertionError("synchronisation did not finish in time")


def test_listing_cards_are_mapped_to_items():
    """Listing cards expose their identifier and price under cardData."""
    body = {
        "data": {
            "cardList": [
                {"cardData": {"id": "111", "title": "标题", "priceInfo": {"price": "12.50"}}},
                {"cardData": {"title": "无 ID 的卡片"}},
            ]
        }
    }

    items, has_more = _extract_items(body)

    assert len(items) == 1
    assert items[0].goodfish_item_id == "111"
    assert items[0].price == "12.50"
    assert items[0].description == ""
    assert has_more is False


def test_invalid_price_is_dropped_from_the_payload():
    """Prices outside the cloud pattern are sent as null instead of failing the batch."""
    assert _safe_price(TEST_PRICE) == TEST_PRICE
    assert _safe_price(TEST_INVALID_PRICE) is None
    assert _safe_price("") is None


def test_payload_uses_the_cloud_contract_field_names():
    """One protocol item becomes one cloud payload with camelCase keys."""
    payload = _to_payload(_item("111"))

    assert payload == {
        "goodfishItemId": "111",
        "title": "标题111",
        "description": None,
        "price": TEST_PRICE,
        "stockStatus": "0",
    }


def test_sync_uploads_the_listing_and_reports_counters():
    """A successful listing read is uploaded and surfaced with its counters."""

    async def scenario():
        cloud = _FakeCloudClient(inserted=2, updated=1)
        service = ItemSyncService(
            _FakeAccountService(),
            _FakeProtocol([_item("111"), _item("222")]),
            cloud,
        )
        await service.start(TEST_ACCOUNT_ID)
        return cloud, await _wait_for_finish(service)

    cloud, status = asyncio.run(scenario())

    assert status.state == ITEM_SYNC_STATE_SUCCESS
    assert status.total == 2
    assert status.inserted == 2
    assert status.updated == 1
    assert cloud.uploaded_goodfish_id == TEST_GOODFISH_ID
    assert [item["goodfishItemId"] for item in cloud.uploaded_items] == ["111", "222"]


def test_empty_listing_succeeds_without_uploading():
    """An account without on-sale items never calls the cloud sync endpoint."""

    async def scenario():
        cloud = _FakeCloudClient(inserted=0, updated=0)
        service = ItemSyncService(_FakeAccountService(), _FakeProtocol([]), cloud)
        await service.start(TEST_ACCOUNT_ID)
        return cloud, await _wait_for_finish(service)

    cloud, status = asyncio.run(scenario())

    assert status.state == ITEM_SYNC_STATE_SUCCESS
    assert status.message == ITEM_SYNC_EMPTY_RESULT_MESSAGE
    assert cloud.uploaded_items == []


def test_protocol_failure_is_reported_as_failed_state():
    """A listing failure ends the run with the protocol message."""

    async def scenario():
        service = ItemSyncService(
            _FakeAccountService(),
            _FakeProtocol([], error=XianyuNetworkError()),
            _FakeCloudClient(inserted=0, updated=0),
        )
        await service.start(TEST_ACCOUNT_ID)
        return await _wait_for_finish(service)

    status = asyncio.run(scenario())

    assert status.state == ITEM_SYNC_STATE_FAILED
    assert status.message == ITEM_SYNC_NETWORK_FAILURE_MESSAGE


def test_unknown_account_state_defaults_to_idle():
    """Status polling before the first run reports an idle state."""

    async def scenario():
        service = ItemSyncService(
            _FakeAccountService(),
            _FakeProtocol([]),
            _FakeCloudClient(inserted=0, updated=0),
        )
        return await service.get_status(TEST_ACCOUNT_ID)

    status = asyncio.run(scenario())

    assert status.state == "idle"
    assert status.total == 0
