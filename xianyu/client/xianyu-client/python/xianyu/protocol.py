"""Stable contract and result types for the Goodfish web protocol."""

from dataclasses import dataclass

from xianyu_client.core.constants import (
    ACCOUNT_GOODFISH_NICKNAME_MAX_LENGTH,
    ITEM_DESCRIPTION_MAX_LENGTH,
    ITEM_GOODFISH_ID_MAX_LENGTH,
    ITEM_STOCK_STATUS_MAX_LENGTH,
    ITEM_TITLE_MAX_LENGTH,
)
from xianyu_client.core.errors import (
    InvalidXianyuCookieError,
    XianyuNetworkError,
    XianyuProtocolError,
    XianyuRiskControlError,
)


@dataclass(frozen=True)
class XianyuAccountInfo:
    """Identity fields extracted after a Cookie has passed protocol verification."""

    goodfish_id: str
    nickname: str

    def bounded(self) -> "XianyuAccountInfo":
        """Return a persistence-safe copy whose nickname fits the local schema.

        Returns:
            A new account info object with a non-empty bounded nickname.
        """
        safe_nickname = self.nickname.strip()[:ACCOUNT_GOODFISH_NICKNAME_MAX_LENGTH]
        if not safe_nickname:
            safe_nickname = self.goodfish_id[:ACCOUNT_GOODFISH_NICKNAME_MAX_LENGTH]
        return XianyuAccountInfo(
            goodfish_id=self.goodfish_id,
            nickname=safe_nickname,
        )

    def belongs_to(self, goodfish_id: str) -> bool:
        """Check whether this verified identity matches another account identity.

        Args:
            goodfish_id: Goodfish ID that the caller expects this Cookie to own.

        Returns:
            True only when both IDs are non-empty and equal.
        """
        return bool(self.goodfish_id and goodfish_id and self.goodfish_id == goodfish_id)


@dataclass(frozen=True)
class XianyuItem:
    """One on-sale item as described by the Goodfish listing page."""

    goodfish_item_id: str
    title: str
    description: str
    price: str
    stock_status: str

    def bounded(self) -> "XianyuItem":
        """Return a copy whose text fields fit the cloud sync contract.

        Returns:
            A new item whose fields are clipped to the cloud column limits.
        """
        return XianyuItem(
            goodfish_item_id=self.goodfish_item_id.strip()[:ITEM_GOODFISH_ID_MAX_LENGTH],
            title=self.title.strip()[:ITEM_TITLE_MAX_LENGTH],
            description=self.description.strip()[:ITEM_DESCRIPTION_MAX_LENGTH],
            price=self.price.strip(),
            stock_status=self.stock_status.strip()[:ITEM_STOCK_STATUS_MAX_LENGTH],
        )


class XianyuProtocol:
    """Abstract boundary hiding Goodfish HTTP details from business services."""

    async def verify_cookie(self, cookie: str) -> XianyuAccountInfo:
        """Verify one Cookie and extract its non-secret account identity.

        Args:
            cookie: Plaintext Cookie that exists only inside this call.

        Returns:
            Verified Goodfish account identity.

        Raises:
            InvalidXianyuCookieError: When the Cookie cannot identify a logged-in account.
            XianyuNetworkError: When Goodfish cannot be reached.
            XianyuProtocolError: When Goodfish returns an unexpected response.
            XianyuRiskControlError: When verification is blocked by risk control.
        """
        raise NotImplementedError

    async def fetch_items(self, cookie: str) -> list["XianyuItem"]:
        """Read every on-sale item of the account owning this Cookie.

        Args:
            cookie: Plaintext Cookie that exists only inside this call.

        Returns:
            On-sale items in Goodfish listing order, capped by the cloud sync limit.

        Raises:
            InvalidXianyuCookieError: When the Cookie cannot identify a logged-in account.
            XianyuNetworkError: When Goodfish cannot be reached.
            XianyuProtocolError: When Goodfish returns an unexpected response.
            XianyuRiskControlError: When reading the listing is blocked by risk control.
        """
        raise NotImplementedError


__all__ = [
    "InvalidXianyuCookieError",
    "XianyuAccountInfo",
    "XianyuItem",
    "XianyuNetworkError",
    "XianyuProtocol",
    "XianyuProtocolError",
    "XianyuRiskControlError",
]
