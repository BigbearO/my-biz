"""Product synchronisation orchestration for local accounts."""

import asyncio
import re
from dataclasses import dataclass
from typing import Optional

from loguru import logger

from xianyu.protocol import XianyuItem, XianyuProtocol
from xianyu_client.core.constants import (
    ITEM_PRICE_PATTERN,
    ITEM_SYNC_CLOUD_FAILURE_MESSAGE,
    ITEM_SYNC_COOKIE_INVALID_MESSAGE,
    ITEM_SYNC_COOKIE_MISMATCH_MESSAGE,
    ITEM_SYNC_EMPTY_RESULT_MESSAGE,
    ITEM_SYNC_FAILED_LOG_MESSAGE,
    ITEM_SYNC_INITIAL_COUNT,
    ITEM_SYNC_MAX_ITEMS,
    ITEM_SYNC_NETWORK_FAILURE_MESSAGE,
    ITEM_SYNC_PROTOCOL_FAILURE_MESSAGE,
    ITEM_SYNC_RISK_CONTROL_MESSAGE,
    ITEM_SYNC_START_LOG_MESSAGE,
    ITEM_SYNC_STATE_FAILED,
    ITEM_SYNC_STATE_IDLE,
    ITEM_SYNC_STATE_RUNNING,
    ITEM_SYNC_STATE_SUCCESS,
    ITEM_SYNC_SUCCESS_LOG_MESSAGE,
    ITEM_SYNC_SUCCESS_MESSAGE,
    ITEM_SYNC_TRUNCATED_MESSAGE,
    ITEM_SYNC_UNKNOWN_FAILURE_MESSAGE,
)
from xianyu_client.core.errors import (
    CloudItemSyncError,
    InvalidXianyuCookieError,
    ItemSyncInProgressError,
    XianyuCookieMismatchError,
    XianyuNetworkError,
    XianyuProtocolError,
    XianyuRiskControlError,
)
from xianyu_client.schemas.account_schemas import AccountDetailResponse
from xianyu_client.schemas.item_schemas import ItemSyncStatusResponse
from xianyu_client.services.account_service import AccountService
from xianyu_client.services.cloud_client import CloudClient

_PRICE_PATTERN = re.compile(ITEM_PRICE_PATTERN)

_ITEM_ID_FIELD = "goodfishItemId"
_ITEM_TITLE_FIELD = "title"
_ITEM_DESCRIPTION_FIELD = "description"
_ITEM_PRICE_FIELD = "price"
_ITEM_STOCK_STATUS_FIELD = "stockStatus"


@dataclass
class ItemSyncStatus:
    """Mutable synchronisation state kept in memory for one account."""

    account_id: int
    state: str = ITEM_SYNC_STATE_IDLE
    total: int = ITEM_SYNC_INITIAL_COUNT
    inserted: int = ITEM_SYNC_INITIAL_COUNT
    updated: int = ITEM_SYNC_INITIAL_COUNT
    unchanged: int = ITEM_SYNC_INITIAL_COUNT
    message: str = ""


class ItemSyncService:
    """Runs one background product synchronisation per account."""

    def __init__(
        self,
        account_service: AccountService,
        protocol: XianyuProtocol,
        cloud_client: CloudClient,
    ) -> None:
        """Create the service bound to the account, protocol, and cloud boundaries.

        Args:
            account_service: Service used to read the decrypted cookie of an account.
            protocol: Goodfish protocol implementation used to read the listing.
            cloud_client: Cloud boundary used to upload the listing.
        """
        self._account_service = account_service
        self._protocol = protocol
        self._cloud_client = cloud_client
        self._statuses: dict[int, ItemSyncStatus] = {}
        self._tasks: dict[int, asyncio.Task] = {}
        self._lock = asyncio.Lock()

    async def start(self, account_id: int) -> ItemSyncStatusResponse:
        """Queue one synchronisation and return its initial state.

        Args:
            account_id: Local database identifier.

        Returns:
            The `running` state of the queued synchronisation.

        Raises:
            AccountNotFoundError: When the account does not exist locally.
            ItemSyncInProgressError: When a synchronisation is already running.
        """
        detail = await self._account_service.get_account_detail(account_id)
        async with self._lock:
            status = self._statuses.get(account_id)
            if status is not None and status.state == ITEM_SYNC_STATE_RUNNING:
                raise ItemSyncInProgressError()
            status = ItemSyncStatus(account_id=account_id, state=ITEM_SYNC_STATE_RUNNING)
            self._statuses[account_id] = status
            self._tasks[account_id] = asyncio.create_task(self._run(status, detail))
            return _to_response(status)

    async def get_status(self, account_id: int) -> ItemSyncStatusResponse:
        """Return the latest known state without starting a synchronisation.

        Args:
            account_id: Local database identifier.

        Returns:
            The stored state, or an idle state when this account never synced.
        """
        async with self._lock:
            status = self._statuses.get(account_id)
            if status is None:
                return _to_response(ItemSyncStatus(account_id=account_id))
            return _to_response(status)

    async def stop(self) -> None:
        """Cancel every in-flight synchronisation during application shutdown."""
        async with self._lock:
            tasks = list(self._tasks.values())
            self._tasks.clear()
        for task in tasks:
            task.cancel()

    async def _run(self, status: ItemSyncStatus, detail: AccountDetailResponse) -> None:
        """Read the listing, upload it, and record the outcome.

        Args:
            status: Mutable state updated in place and read by status polling.
            detail: Account representation carrying the decrypted cookie.
        """
        logger.info(ITEM_SYNC_START_LOG_MESSAGE, status.account_id, detail.goodfish_id)
        try:
            items = await self._protocol.fetch_items(detail.cookie)
            status.total = len(items)
            if not items:
                status.state = ITEM_SYNC_STATE_SUCCESS
                status.message = ITEM_SYNC_EMPTY_RESULT_MESSAGE
                return
            payload = [_to_payload(item) for item in items[:ITEM_SYNC_MAX_ITEMS]]
            inserted, updated, unchanged = await self._cloud_client.sync_items(
                detail.goodfish_id, payload
            )
            status.inserted = inserted
            status.updated = updated
            status.unchanged = unchanged
            status.state = ITEM_SYNC_STATE_SUCCESS
            status.message = _success_message(status.total, inserted, updated, unchanged)
            logger.info(
                ITEM_SYNC_SUCCESS_LOG_MESSAGE,
                status.account_id,
                status.total,
                inserted,
                updated,
                unchanged,
            )
        except Exception as error:  # noqa: BLE001 - every failure must surface as a status
            status.state = ITEM_SYNC_STATE_FAILED
            status.message = _failure_message(error)
            logger.warning(ITEM_SYNC_FAILED_LOG_MESSAGE, status.account_id, error)
        finally:
            async with self._lock:
                self._tasks.pop(status.account_id, None)


def _success_message(total: int, inserted: int, updated: int, unchanged: int) -> str:
    """Describe one finished synchronisation in operator terms.

    Args:
        total: On-sale items read from Goodfish after de-duplication.
        inserted: Items newly created by the cloud.
        updated: Items whose content actually changed.
        unchanged: Items re-reported without any content change.

    Returns:
        A message that explains the counters instead of only the delta.
    """
    message = ITEM_SYNC_SUCCESS_MESSAGE.format(total, inserted, updated, unchanged)
    if total >= ITEM_SYNC_MAX_ITEMS:
        message += ITEM_SYNC_TRUNCATED_MESSAGE.format(ITEM_SYNC_MAX_ITEMS)
    return message


def _failure_message(error: Exception) -> str:
    """Translate one synchronisation failure into an operator-facing message.

    Args:
        error: Exception raised while reading or uploading the listing.

    Returns:
        A localized hint; unknown failures keep their original text for the log.
    """
    if isinstance(error, InvalidXianyuCookieError):
        return ITEM_SYNC_COOKIE_INVALID_MESSAGE
    if isinstance(error, XianyuCookieMismatchError):
        return ITEM_SYNC_COOKIE_MISMATCH_MESSAGE
    if isinstance(error, XianyuRiskControlError):
        return ITEM_SYNC_RISK_CONTROL_MESSAGE
    if isinstance(error, XianyuNetworkError):
        return ITEM_SYNC_NETWORK_FAILURE_MESSAGE
    if isinstance(error, XianyuProtocolError):
        return ITEM_SYNC_PROTOCOL_FAILURE_MESSAGE
    if isinstance(error, CloudItemSyncError):
        return ITEM_SYNC_CLOUD_FAILURE_MESSAGE
    return str(error) or ITEM_SYNC_UNKNOWN_FAILURE_MESSAGE


def _to_payload(item: XianyuItem) -> dict:
    """Shape one protocol item for the cloud sync contract.

    Args:
        item: Item read from the Goodfish listing.

    Returns:
        A payload whose empty fields are omitted and whose price is contract-safe.
    """
    return {
        _ITEM_ID_FIELD: item.goodfish_item_id,
        _ITEM_TITLE_FIELD: item.title or None,
        _ITEM_DESCRIPTION_FIELD: item.description or None,
        _ITEM_PRICE_FIELD: _safe_price(item.price),
        _ITEM_STOCK_STATUS_FIELD: item.stock_status or None,
    }


def _safe_price(price: str) -> Optional[str]:
    """Keep only prices that satisfy the cloud validation pattern.

    Args:
        price: Raw price text read from Goodfish.

    Returns:
        The price text when it matches `ITEM_PRICE_PATTERN`, otherwise `None`.
    """
    if price and _PRICE_PATTERN.fullmatch(price):
        return price
    return None


def _to_response(status: ItemSyncStatus) -> ItemSyncStatusResponse:
    """Copy the mutable state into its response model.

    Args:
        status: State stored in memory.

    Returns:
        An immutable response snapshot.
    """
    return ItemSyncStatusResponse(
        account_id=status.account_id,
        state=status.state,
        total=status.total,
        inserted=status.inserted,
        updated=status.updated,
        unchanged=status.unchanged,
        message=status.message,
    )
