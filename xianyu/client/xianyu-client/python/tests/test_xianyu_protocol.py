"""Tests for the local Goodfish protocol helpers."""

from types import SimpleNamespace

import pytest
from multidict import CIMultiDict

from xianyu.http_protocol import (
    _LoginUserResult,
    _extract_account_info,
    _generate_sign,
    _merge_response_cookies,
    _parse_cookies,
)
from xianyu_client.core.errors import (
    InvalidXianyuCookieError,
    XianyuProtocolError,
    XianyuRiskControlError,
)


TEST_COOKIE = "unb=2038890; tracknick=%E4%B9%B0%E5%AE%B6; _m_h5_tk=token_1"


def test_cookie_identity_and_nickname_are_extracted():
    """Cookie identity extraction decodes the nickname without network access."""
    account_info = _extract_account_info(_parse_cookies(TEST_COOKIE))

    assert account_info.goodfish_id == "2038890"
    assert account_info.nickname == "买家"


def test_missing_goodfish_identity_is_rejected():
    """Cookies without either supported account identifier are invalid."""
    with pytest.raises(InvalidXianyuCookieError):
        _extract_account_info({"tracknick": "buyer"})


def test_bounded_identity_falls_back_to_goodfish_id():
    """An absent nickname is replaced by the verified account identifier."""
    account_info = _extract_account_info({"munb": "404404"}).bounded()

    assert account_info.nickname == "404404"
    assert account_info.belongs_to("404404")
    assert not account_info.belongs_to("2038890")


def test_sign_changes_with_token_and_is_md5_shaped():
    """The signing helper includes the mtop token in its digest material."""
    first_sign = _generate_sign("1760000000000", "token-one", "{}")
    second_sign = _generate_sign("1760000000000", "token-two", "{}")

    assert len(first_sign) == 32
    assert first_sign != second_sign


def test_response_cookies_are_merged_for_retry():
    """Token cookies returned by mtop can refresh one in-memory retry."""
    headers = CIMultiDict([("Set-Cookie", "_m_h5_tk=token_2; Path=/")])
    response = SimpleNamespace(headers=headers)

    refreshed_cookie = _merge_response_cookies(response, TEST_COOKIE)

    assert "_m_h5_tk=token_2" in refreshed_cookie
    assert "unb=2038890" in refreshed_cookie


def test_protocol_errors_are_classified_without_secret_material():
    """mtop return markers map to stable domain errors."""
    with pytest.raises(InvalidXianyuCookieError):
        _LoginUserResult(False, "FAIL_SYS_SESSION_EXPIRED").raise_protocol_error()
    with pytest.raises(XianyuRiskControlError):
        _LoginUserResult(False, "RGV587_ERROR::PLEASE_VALIDATE").raise_protocol_error()
    with pytest.raises(XianyuProtocolError):
        _LoginUserResult(False, "FAIL_SYS_UNKNOWN").raise_protocol_error()
