"""Minimal signed Goodfish mtop client used for Cookie validation."""

import hashlib
import time
from collections.abc import Mapping
from urllib.parse import unquote

import aiohttp

from xianyu.protocol import XianyuAccountInfo, XianyuProtocol
from xianyu_client.core.constants import (
    XIANYU_COOKIE_NICKNAME_KEY,
    XIANYU_COOKIE_TOKEN_KEY,
    XIANYU_COOKIE_USER_ID_KEYS,
    XIANYU_MTOP_ACCOUNT_SITE,
    XIANYU_MTOP_API_LOGIN_USER_GET,
    XIANYU_MTOP_API_VERSION,
    XIANYU_MTOP_APP_KEY,
    XIANYU_MTOP_BASE_URL,
    XIANYU_MTOP_DATA_TYPE,
    XIANYU_MTOP_JSV,
    XIANYU_MTOP_RESPONSE_TYPE,
    XIANYU_MTOP_SESSION_OPTION,
    XIANYU_MTOP_TIMEOUT_MILLISECONDS,
    XIANYU_PROTOCOL_TIMEOUT_SECONDS,
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
        raise XianyuProtocolError()


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
    url = f"{XIANYU_MTOP_BASE_URL}/{XIANYU_MTOP_API_LOGIN_USER_GET}/{XIANYU_MTOP_API_VERSION}/"
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
            if response.status != 200:
                raise XianyuProtocolError()
            try:
                body = await response.json(content_type=None)
            except (aiohttp.ContentTypeError, ValueError) as error:
                raise XianyuProtocolError() from error
    except (aiohttp.ClientError, TimeoutError) as error:
        raise XianyuNetworkError() from error

    if not isinstance(body, Mapping):
        raise XianyuProtocolError()
    ret_values = body.get("ret", [])
    ret_message = str(ret_values[0]) if ret_values else ""
    return _LoginUserResult(
        is_success=_SUCCESS_MARKER in ret_message,
        ret_message=ret_message,
        refreshed_cookie=refreshed_cookie,
    )


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
