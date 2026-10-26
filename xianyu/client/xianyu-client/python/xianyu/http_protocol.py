"""Minimal signed Goodfish mtop client used for Cookie validation and item reads."""

import asyncio
import hashlib
import json
import time
from collections.abc import Mapping
from urllib.parse import unquote

import aiohttp
from loguru import logger

from xianyu.protocol import XianyuAccountInfo, XianyuItem, XianyuProtocol
from xianyu_client.core.constants import (
    COOKIE_KEY_JOINER,
    ITEM_SYNC_MAX_ITEMS,
    ITEM_SYNC_TRUNCATED_LOG_MESSAGE,
    XIANYU_COOKIE_NICKNAME_KEY,
    XIANYU_COOKIE_TOKEN_KEY,
    XIANYU_COOKIE_USER_ID_KEYS,
    XIANYU_ITEM_EMPTY_TEXT,
    XIANYU_ITEM_GROUP_ID_ON_SALE,
    XIANYU_ITEM_GROUP_NAME_ON_SALE,
    XIANYU_ITEM_MAX_PAGES,
    XIANYU_ITEM_PAGE_INTERVAL_SECONDS,
    XIANYU_ITEM_PAGE_SIZE,
    XIANYU_ITEM_RESPONSE_CARD_DATA_KEY,
    XIANYU_ITEM_RESPONSE_CARD_LIST_KEY,
    XIANYU_ITEM_RESPONSE_DATA_KEY,
    XIANYU_ITEM_RESPONSE_ID_KEY,
    XIANYU_ITEM_RESPONSE_PRICE_INFO_KEY,
    XIANYU_ITEM_RESPONSE_PRICE_KEY,
    XIANYU_ITEM_RESPONSE_STATUS_KEY,
    XIANYU_ITEM_RESPONSE_TITLE_KEY,
    XIANYU_MTOP_ACCOUNT_SITE,
    XIANYU_MTOP_API_ITEM_LIST,
    XIANYU_MTOP_API_LOGIN_USER_GET,
    XIANYU_MTOP_API_VERSION,
    XIANYU_MTOP_APP_KEY,
    XIANYU_MTOP_BASE_URL,
    XIANYU_MTOP_DATA_TYPE,
    XIANYU_MTOP_ITEM_LIST_SPM_CNT,
    XIANYU_MTOP_ITEM_LIST_SPM_PRE,
    XIANYU_MTOP_JSV,
    XIANYU_MTOP_LOGIN_SPM_CNT,
    XIANYU_MTOP_PATH_PREFIX,
    XIANYU_MTOP_RESPONSE_TYPE,
    XIANYU_MTOP_SESSION_OPTION,
    XIANYU_MTOP_TIMEOUT_MILLISECONDS,
    XIANYU_PROTOCOL_BODY_LOG_MESSAGE,
    XIANYU_PROTOCOL_DETAIL_BODY,
    XIANYU_PROTOCOL_DETAIL_HTTP_STATUS,
    XIANYU_PROTOCOL_DETAIL_SHAPE,
    XIANYU_PROTOCOL_HTML_BODY_MARKER,
    XIANYU_PROTOCOL_HTTP_STATUS_LOG_MESSAGE,
    XIANYU_PROTOCOL_RET_LOG_MESSAGE,
    XIANYU_PROTOCOL_SHAPE_LOG_MESSAGE,
    XIANYU_PROTOCOL_TIMEOUT_SECONDS,
    XIANYU_MTOP_RESPONSE_RET_KEY,
    HTTP_STATUS_OK,
    XIANYU_TOKEN_RETRY_LIMIT,
    XIANYU_WEB_ORIGIN,
)
from xianyu_client.core.errors import (
    InvalidXianyuCookieError,
    XianyuNetworkError,
    XianyuProtocolError,
    XianyuRiskControlError,
)


class HttpXianyuProtocol(XianyuProtocol):
    """Validates Cookies with one signed `loginuser.get` mtop request."""

    async def verify_cookie(self, cookie: str) -> XianyuAccountInfo:
        """Verify one Cookie without persistently renewing its token.

        Args:
            cookie: Plaintext Cookie supplied by the operator.

        Returns:
            The verified Goodfish ID and nickname carried by that Cookie.

        Raises:
            InvalidXianyuCookieError: When required identity fields or login state are absent.
            XianyuNetworkError: When the request cannot reach Goodfish.
            XianyuProtocolError: When the response cannot be interpreted safely.
            XianyuRiskControlError: When Goodfish requires risk-control verification.
        """
        current_cookie = _sanitize_cookie(cookie)
        cookies = _parse_cookies(current_cookie)
        account_info = _extract_account_info(cookies)
        request_cookie = current_cookie

        async with aiohttp.ClientSession(
            timeout=aiohttp.ClientTimeout(total=XIANYU_PROTOCOL_TIMEOUT_SECONDS)
        ) as session:
            for _ in range(XIANYU_TOKEN_RETRY_LIMIT):
                payload = await _request_login_user(session, request_cookie)
                if payload.is_success:
                    return account_info.bounded()
                if payload.refreshed_cookie:
                    request_cookie = payload.refreshed_cookie
                    continue
                payload.raise_protocol_error()

        raise InvalidXianyuCookieError()

    async def fetch_items(self, cookie: str) -> list[XianyuItem]:
        """Read every on-sale item of the account owning this Cookie.

        The listing is read page by page from the personal Goodfish site
        (`mtop.idle.web.xyh.item.list`, group 在售) because the cloud sync
        contract stores at most `ITEM_SYNC_MAX_ITEMS` items per call.

        Args:
            cookie: Plaintext Cookie supplied by the local account store.

        Returns:
            On-sale items clipped to the cloud column limits.

        Raises:
            InvalidXianyuCookieError: When the Cookie cannot identify a logged-in account.
            XianyuNetworkError: When the request cannot reach Goodfish.
            XianyuProtocolError: When a page cannot be interpreted safely.
            XianyuRiskControlError: When Goodfish requires risk-control verification.
        """
        current_cookie = _sanitize_cookie(cookie)
        user_id = _extract_account_info(_parse_cookies(current_cookie)).goodfish_id
        collected: list[XianyuItem] = []
        async with aiohttp.ClientSession(
            timeout=aiohttp.ClientTimeout(total=XIANYU_PROTOCOL_TIMEOUT_SECONDS)
        ) as session:
            for page_number in range(1, XIANYU_ITEM_MAX_PAGES + 1):
                page = await _request_item_page(session, current_cookie, user_id, page_number)
                if page.refreshed_cookie:
                    current_cookie = page.refreshed_cookie
                collected.extend(page.items)
                if len(collected) >= ITEM_SYNC_MAX_ITEMS:
                    logger.warning(
                        ITEM_SYNC_TRUNCATED_LOG_MESSAGE,
                        ITEM_SYNC_MAX_ITEMS,
                        user_id,
                    )
                    collected = collected[:ITEM_SYNC_MAX_ITEMS]
                    break
                if not page.has_more:
                    break
                await asyncio.sleep(XIANYU_ITEM_PAGE_INTERVAL_SECONDS)
        return [item.bounded() for item in collected]


class _LoginUserResult:
    """Internal classification of one mtop response."""

    def __init__(
        self,
        is_success: bool,
        ret_message: str,
        refreshed_cookie: str = "",
    ) -> None:
        """Create a response classification.

        Args:
            is_success: Whether mtop returned its success marker.
            ret_message: First sanitized `ret` entry from mtop.
            refreshed_cookie: In-memory Cookie updated from `Set-Cookie`, if present.
        """
        self.is_success = is_success
        self.ret_message = ret_message
        self.refreshed_cookie = refreshed_cookie

    def raise_protocol_error(self) -> None:
        """Convert the response classification to a stable domain error.

        Raises:
            InvalidXianyuCookieError: For expired sessions or missing login tokens.
            XianyuRiskControlError: For captcha, punish, or machine-verification responses.
            XianyuProtocolError: For all other non-success mtop responses.
        """
        message = self.ret_message
        if any(marker in message for marker in _INVALID_COOKIE_MARKERS):
            raise InvalidXianyuCookieError()
        if any(marker in message for marker in _RISK_CONTROL_MARKERS):
            raise XianyuRiskControlError()
        raise XianyuProtocolError(detail=message)


class _ItemPageResult(_LoginUserResult):
    """Internal classification of one item-list page response."""

    def __init__(
        self,
        is_success: bool,
        ret_message: str,
        items: list[XianyuItem],
        has_more: bool,
        refreshed_cookie: str = "",
    ) -> None:
        """Create a page classification.

        Args:
            is_success: Whether mtop returned its success marker.
            ret_message: First sanitized `ret` entry from mtop.
            items: Items extracted from this page.
            has_more: Whether another page may still hold items.
            refreshed_cookie: In-memory Cookie updated from `Set-Cookie`, if present.
        """
        super().__init__(is_success, ret_message, refreshed_cookie)
        self.items = items
        self.has_more = has_more


_INVALID_COOKIE_MARKERS = (
    "SESSION_EXPIRED",
    "TOKEN_EMPTY",
    "TOKEN_EXOIRED",
    "TOKEN_EXPIRED",
)
_RISK_CONTROL_MARKERS = (
    "FAIL_SYS_USER_VALIDATE",
    "RGV587",
    "FAIL_SYS_ILLEGAL_ACCESS",
    "FAIL_BIZ_WUA_IS_MACHINE",
    "WUA_IS_MACHINE",
    "punish",
    "captcha",
    "validate",
)
_EMPTY_JSON_PAYLOAD = "{}"
_SUCCESS_MARKER = "SUCCESS"
_RET_KEY = XIANYU_MTOP_RESPONSE_RET_KEY
_HTTP_STATUS_OK = HTTP_STATUS_OK


async def _request_login_user(session: aiohttp.ClientSession, cookie: str) -> _LoginUserResult:
    """Issue one signed `loginuser.get` request.

    Args:
        session: Active HTTP session with the configured timeout.
        cookie: Sanitized Cookie used for this attempt only.

    Returns:
        A classified mtop response, including refreshed Cookie material when supplied.

    Raises:
        XianyuNetworkError: When transport fails or times out.
        XianyuProtocolError: When HTTP status or JSON shape is unexpected.
    """
    timestamp = str(int(time.time() * 1000))
    token = _parse_cookies(cookie).get(XIANYU_COOKIE_TOKEN_KEY, "")
    token_seed = token.split("_", 1)[0] if token else ""
    sign = _generate_sign(timestamp, token_seed, _EMPTY_JSON_PAYLOAD)
    url = _build_mtop_url(XIANYU_MTOP_API_LOGIN_USER_GET, XIANYU_MTOP_API_VERSION)
    params = {
        "jsv": XIANYU_MTOP_JSV,
        "appKey": XIANYU_MTOP_APP_KEY,
        "t": timestamp,
        "sign": sign,
        "v": XIANYU_MTOP_API_VERSION,
        "type": XIANYU_MTOP_RESPONSE_TYPE,
        "accountSite": XIANYU_MTOP_ACCOUNT_SITE,
        "dataType": XIANYU_MTOP_DATA_TYPE,
        "timeout": XIANYU_MTOP_TIMEOUT_MILLISECONDS,
        "api": XIANYU_MTOP_API_LOGIN_USER_GET,
        "sessionOption": XIANYU_MTOP_SESSION_OPTION,
        "spm_cnt": XIANYU_MTOP_LOGIN_SPM_CNT,
    }
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/x-www-form-urlencoded",
        "Origin": XIANYU_WEB_ORIGIN,
        "Referer": f"{XIANYU_WEB_ORIGIN}/",
        "User-Agent": _USER_AGENT,
        "Cookie": cookie,
    }
    try:
        async with session.post(
            url,
            params=params,
            data={"data": _EMPTY_JSON_PAYLOAD},
            headers=headers,
        ) as response:
            refreshed_cookie = _merge_response_cookies(response, cookie)
            if response.status != _HTTP_STATUS_OK:
                logger.warning(
                    XIANYU_PROTOCOL_HTTP_STATUS_LOG_MESSAGE,
                    XIANYU_MTOP_API_LOGIN_USER_GET,
                    response.status,
                    _cookie_keys(cookie),
                )
                raise XianyuProtocolError(
                    detail=XIANYU_PROTOCOL_DETAIL_HTTP_STATUS.format(
                        response.status, XIANYU_MTOP_API_LOGIN_USER_GET
                    )
                )
            try:
                body = await response.json(content_type=None)
            except (aiohttp.ContentTypeError, ValueError) as error:
                raise (await _unreadable_body_error(response, XIANYU_MTOP_API_LOGIN_USER_GET)) from error
    except (aiohttp.ClientError, TimeoutError) as error:
        raise XianyuNetworkError() from error

    if not isinstance(body, Mapping):
        logger.warning(XIANYU_PROTOCOL_SHAPE_LOG_MESSAGE, XIANYU_MTOP_API_LOGIN_USER_GET, type(body))
        raise XianyuProtocolError(
            detail=XIANYU_PROTOCOL_DETAIL_SHAPE.format(type(body), XIANYU_MTOP_API_LOGIN_USER_GET)
        )
    ret_values = body.get(_RET_KEY, [])
    ret_message = str(ret_values[0]) if ret_values else ""
    is_success = _SUCCESS_MARKER in ret_message
    if not is_success:
        logger.warning(XIANYU_PROTOCOL_RET_LOG_MESSAGE, XIANYU_MTOP_API_LOGIN_USER_GET, ret_message)
    return _LoginUserResult(
        is_success=is_success,
        ret_message=ret_message,
        refreshed_cookie=refreshed_cookie,
    )


async def _request_item_page(
    session: aiohttp.ClientSession,
    cookie: str,
    user_id: str,
    page_number: int,
) -> _ItemPageResult:
    """Read one listing page, retrying once after an in-memory token refresh.

    Args:
        session: Active HTTP session with the configured timeout.
        cookie: Sanitized Cookie used for the first attempt.
        user_id: Goodfish ID that owns the Cookie.
        page_number: One-based listing page number.

    Returns:
        The classified page, including a refreshed Cookie when Goodfish supplied one.

    Raises:
        InvalidXianyuCookieError: When no attempt could be authenticated.
        XianyuNetworkError: When transport fails or times out.
        XianyuProtocolError: When a response cannot be interpreted safely.
        XianyuRiskControlError: When risk control intercepts the listing.
    """
    request_cookie = cookie
    for _ in range(XIANYU_TOKEN_RETRY_LIMIT):
        result = await _request_item_page_once(session, request_cookie, user_id, page_number)
        if result.is_success:
            return result
        if result.refreshed_cookie:
            request_cookie = result.refreshed_cookie
            continue
        result.raise_protocol_error()
    raise InvalidXianyuCookieError()


async def _request_item_page_once(
    session: aiohttp.ClientSession,
    cookie: str,
    user_id: str,
    page_number: int,
) -> _ItemPageResult:
    """Issue one signed `item.list` request for a single page.

    Args:
        session: Active HTTP session with the configured timeout.
        cookie: Sanitized Cookie used for this attempt only.
        user_id: Goodfish ID that owns the Cookie.
        page_number: One-based listing page number.

    Returns:
        A classified page response, including refreshed Cookie material when supplied.

    Raises:
        XianyuNetworkError: When transport fails or times out.
        XianyuProtocolError: When HTTP status or JSON shape is unexpected.
    """
    timestamp = str(int(time.time() * 1000))
    token = _parse_cookies(cookie).get(XIANYU_COOKIE_TOKEN_KEY, "")
    token_seed = token.split("_", 1)[0] if token else ""
    data = json.dumps(
        {
            "needGroupInfo": False,
            "pageNumber": page_number,
            "pageSize": XIANYU_ITEM_PAGE_SIZE,
            "groupName": XIANYU_ITEM_GROUP_NAME_ON_SALE,
            "groupId": XIANYU_ITEM_GROUP_ID_ON_SALE,
            "defaultGroup": True,
            "userId": user_id,
        },
        separators=(",", ":"),
    )
    sign = _generate_sign(timestamp, token_seed, data)
    url = _build_mtop_url(XIANYU_MTOP_API_ITEM_LIST, XIANYU_MTOP_API_VERSION)
    params = {
        "jsv": XIANYU_MTOP_JSV,
        "appKey": XIANYU_MTOP_APP_KEY,
        "t": timestamp,
        "sign": sign,
        "v": XIANYU_MTOP_API_VERSION,
        "type": XIANYU_MTOP_RESPONSE_TYPE,
        "accountSite": XIANYU_MTOP_ACCOUNT_SITE,
        "dataType": XIANYU_MTOP_DATA_TYPE,
        "timeout": XIANYU_MTOP_TIMEOUT_MILLISECONDS,
        "api": XIANYU_MTOP_API_ITEM_LIST,
        "sessionOption": XIANYU_MTOP_SESSION_OPTION,
        "spm_cnt": XIANYU_MTOP_ITEM_LIST_SPM_CNT,
        "spm_pre": XIANYU_MTOP_ITEM_LIST_SPM_PRE,
    }
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/x-www-form-urlencoded",
        "Origin": XIANYU_WEB_ORIGIN,
        "Referer": f"{XIANYU_WEB_ORIGIN}/",
        "User-Agent": _USER_AGENT,
        "Cookie": cookie,
    }
    try:
        async with session.post(
            url,
            params=params,
            data={"data": data},
            headers=headers,
        ) as response:
            refreshed_cookie = _merge_response_cookies(response, cookie)
            if response.status != _HTTP_STATUS_OK:
                logger.warning(
                    XIANYU_PROTOCOL_HTTP_STATUS_LOG_MESSAGE,
                    XIANYU_MTOP_API_ITEM_LIST,
                    response.status,
                    _cookie_keys(cookie),
                )
                raise XianyuProtocolError(
                    detail=XIANYU_PROTOCOL_DETAIL_HTTP_STATUS.format(
                        response.status, XIANYU_MTOP_API_ITEM_LIST
                    )
                )
            try:
                body = await response.json(content_type=None)
            except (aiohttp.ContentTypeError, ValueError) as error:
                raise (await _unreadable_body_error(response, XIANYU_MTOP_API_ITEM_LIST)) from error
    except (aiohttp.ClientError, TimeoutError) as error:
        raise XianyuNetworkError() from error

    if not isinstance(body, Mapping):
        logger.warning(XIANYU_PROTOCOL_SHAPE_LOG_MESSAGE, XIANYU_MTOP_API_ITEM_LIST, type(body))
        raise XianyuProtocolError(
            detail=XIANYU_PROTOCOL_DETAIL_SHAPE.format(type(body), XIANYU_MTOP_API_ITEM_LIST)
        )
    ret_values = body.get(_RET_KEY, [])
    ret_message = str(ret_values[0]) if ret_values else ""
    is_success = _SUCCESS_MARKER in ret_message
    if not is_success:
        logger.warning(XIANYU_PROTOCOL_RET_LOG_MESSAGE, XIANYU_MTOP_API_ITEM_LIST, ret_message)
    items, has_more = _extract_items(body) if is_success else ([], False)
    return _ItemPageResult(
        is_success=is_success,
        ret_message=ret_message,
        items=items,
        has_more=has_more,
        refreshed_cookie=refreshed_cookie,
    )


def _extract_items(body: Mapping) -> tuple[list[XianyuItem], bool]:
    """Read the item cards of one listing page.

    Args:
        body: Decoded mtop response of a successful page request.

    Returns:
        The items carried by this page and whether another page may follow.

    Notes:
        The listing response carries no description, so `description` stays empty;
        the operator edits it in the management UI instead.
    """
    data = body.get(XIANYU_ITEM_RESPONSE_DATA_KEY)
    if not isinstance(data, Mapping):
        logger.warning(
            XIANYU_PROTOCOL_SHAPE_LOG_MESSAGE,
            XIANYU_MTOP_API_ITEM_LIST,
            sorted(body.keys()),
        )
        return [], False
    cards = data.get(XIANYU_ITEM_RESPONSE_CARD_LIST_KEY)
    if not isinstance(cards, list):
        logger.warning(
            XIANYU_PROTOCOL_SHAPE_LOG_MESSAGE,
            XIANYU_MTOP_API_ITEM_LIST,
            sorted(data.keys()),
        )
        return [], False
    items: list[XianyuItem] = []
    for card in cards:
        if not isinstance(card, Mapping):
            continue
        card_data = card.get(XIANYU_ITEM_RESPONSE_CARD_DATA_KEY)
        if not isinstance(card_data, Mapping):
            continue
        item_id = _to_text(card_data.get(XIANYU_ITEM_RESPONSE_ID_KEY))
        if not item_id:
            continue
        items.append(
            XianyuItem(
                goodfish_item_id=item_id,
                title=_to_text(card_data.get(XIANYU_ITEM_RESPONSE_TITLE_KEY)),
                description=XIANYU_ITEM_EMPTY_TEXT,
                price=_extract_price(card_data),
                stock_status=_to_text(card_data.get(XIANYU_ITEM_RESPONSE_STATUS_KEY)),
            )
        )
    return items, len(items) >= XIANYU_ITEM_PAGE_SIZE


def _extract_price(card_data: Mapping) -> str:
    """Read the display price of one listing card.

    Args:
        card_data: `cardData` object of one listing card.

    Returns:
        The raw price text, or an empty string when the card carries none.
    """
    price_info = card_data.get(XIANYU_ITEM_RESPONSE_PRICE_INFO_KEY)
    if not isinstance(price_info, Mapping):
        return XIANYU_ITEM_EMPTY_TEXT
    return _to_text(price_info.get(XIANYU_ITEM_RESPONSE_PRICE_KEY))


def _to_text(value: object) -> str:
    """Convert one optional response field to trimmed text.

    Args:
        value: Raw JSON value.

    Returns:
        The trimmed text, or an empty string for `None`.
    """
    if value is None:
        return XIANYU_ITEM_EMPTY_TEXT
    return str(value).strip()


def _sanitize_cookie(cookie: str) -> str:
    """Remove line breaks before the Cookie is sent to Goodfish.

    Args:
        cookie: Raw operator input.

    Returns:
        A single-line Cookie string.

    Raises:
        InvalidXianyuCookieError: When the input is empty after trimming.
    """
    sanitized = cookie.replace("\r", "").replace("\n", "").strip()
    if not sanitized:
        raise InvalidXianyuCookieError()
    return sanitized


def _build_mtop_url(api: str, version: str) -> str:
    """Build the signed mtop endpoint for one API call.

    Args:
        api: mtop API name, e.g. `mtop.taobao.idlemessage.pc.loginuser.get`.
        version: mtop API version, which must match the `v` request parameter.

    Returns:
        The absolute endpoint including the mandatory `/h5` path prefix.
    """
    return f"{XIANYU_MTOP_BASE_URL}{XIANYU_MTOP_PATH_PREFIX}/{api}/{version}/"


def _cookie_keys(cookie: str) -> str:
    """List the Cookie field names for diagnostics without exposing values.

    Args:
        cookie: Single-line Cookie input.

    Returns:
        Comma-joined field names in input order.
    """
    return COOKIE_KEY_JOINER.join(_parse_cookies(cookie).keys())


async def _unreadable_body_error(
    response: aiohttp.ClientResponse, api: str
) -> XianyuProtocolError:
    """Classify a non-JSON mtop body and record its shape only.

    Args:
        response: Completed response whose body could not be decoded as JSON.
        api: mtop API name used for the log and the diagnostic detail.

    Returns:
        A protocol error carrying a non-secret diagnostic detail.
    """
    content_type = response.headers.get("Content-Type", XIANYU_ITEM_EMPTY_TEXT)
    try:
        body_text = await response.text()
    except (aiohttp.ClientError, TimeoutError, UnicodeDecodeError):
        body_text = XIANYU_ITEM_EMPTY_TEXT
    logger.warning(
        XIANYU_PROTOCOL_BODY_LOG_MESSAGE,
        api,
        response.status,
        content_type,
        len(body_text),
        body_text.startswith(XIANYU_PROTOCOL_HTML_BODY_MARKER),
    )
    return XianyuProtocolError(detail=XIANYU_PROTOCOL_DETAIL_BODY.format(api, content_type))


def _parse_cookies(cookie: str) -> dict[str, str]:
    """Parse a Cookie header into a dictionary.

    Args:
        cookie: Single-line Cookie input.

    Returns:
        All valid `key=value` pairs in input order.
    """
    cookies: dict[str, str] = {}
    for part in cookie.split(";"):
        item = part.strip()
        if not item or "=" not in item:
            continue
        key, value = item.split("=", 1)
        key = key.strip()
        if key:
            cookies[key] = value.strip()
    return cookies


def _extract_account_info(cookies: Mapping[str, str]) -> XianyuAccountInfo:
    """Extract account identity from required Cookie fields.

    Args:
        cookies: Parsed Cookie pairs.

    Returns:
        Account identity with a nickname fallback to the Goodfish ID.

    Raises:
        InvalidXianyuCookieError: When neither `unb` nor `munb` is present.
    """
    goodfish_id = next(
        (cookies[key].strip() for key in XIANYU_COOKIE_USER_ID_KEYS if cookies.get(key, "").strip()),
        "",
    )
    if not goodfish_id:
        raise InvalidXianyuCookieError()
    nickname = unquote(cookies.get(XIANYU_COOKIE_NICKNAME_KEY, "")).strip()
    return XianyuAccountInfo(goodfish_id=goodfish_id, nickname=nickname)


def _generate_sign(timestamp: str, token: str, data: str) -> str:
    """Generate the MD5 signature used by Goodfish mtop.

    Args:
        timestamp: Current millisecond timestamp as text.
        token: First segment of `_m_h5_tk`, or an empty string when absent.
        data: Canonical JSON payload.

    Returns:
        The hexadecimal request signature.
    """
    material = f"{token}&{timestamp}&{XIANYU_MTOP_APP_KEY}&{data}"
    return hashlib.md5(material.encode("utf-8"), usedforsecurity=False).hexdigest()


def _merge_response_cookies(response: aiohttp.ClientResponse, original_cookie: str) -> str:
    """Merge response `Set-Cookie` values for one in-memory retry only.

    Args:
        response: Completed HTTP response whose headers can be read.
        original_cookie: Cookie used for the request.

    Returns:
        Updated Cookie text, or an empty string when no usable value was returned.
    """
    cookies = _parse_cookies(original_cookie)
    changed = False
    for header in response.headers.getall("Set-Cookie", []):
        pair = header.split(";", 1)[0]
        if "=" not in pair:
            continue
        key, value = pair.split("=", 1)
        key = key.strip()
        value = value.strip()
        if key and value:
            cookies[key] = value
            changed = True
    if not changed:
        return ""
    return "; ".join(f"{key}={value}" for key, value in cookies.items())


_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
)


__all__ = ["HttpXianyuProtocol"]
