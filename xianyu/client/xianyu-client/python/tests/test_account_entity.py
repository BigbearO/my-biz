"""Tests for behavior concentrated on the Goodfish account entity."""

from datetime import datetime, timezone

from xianyu_client.core.constants import (
    ACCOUNT_INITIAL_IS_VALID,
    ACCOUNT_INITIAL_REMARK,
    ACCOUNT_LISTEN_STATE_IDLE,
)
from xianyu_client.db.models import GoodfishAccountModel


TEST_GOODFISH_ID = "2038890"
TEST_GOODFISH_NICKNAME = "buyer-one"
TEST_ENCRYPTED_COOKIE = "dmFsaWQtY3J5cHRvZGF0YQ=="


def _new_account() -> GoodfishAccountModel:
    """Create an account with its required persistence fields.

    Returns:
        An unsaved local account entity.
    """
    return GoodfishAccountModel(
        goodfish_id=TEST_GOODFISH_ID,
        goodfish_nickname=TEST_GOODFISH_NICKNAME,
        cookie=TEST_ENCRYPTED_COOKIE,
        remark=ACCOUNT_INITIAL_REMARK,
        is_valid=ACCOUNT_INITIAL_IS_VALID,
        listen_state=ACCOUNT_LISTEN_STATE_IDLE,
    )


def test_new_account_is_not_a_listening_candidate():
    """A new account remains inactive until cookie validation succeeds."""
    account = _new_account()

    assert account.listen_state == ACCOUNT_LISTEN_STATE_IDLE
    assert account.is_valid is ACCOUNT_INITIAL_IS_VALID
    assert not account.is_listening_candidate()


def test_mark_cookie_validation_uses_supplied_timestamp():
    """Entity validation updates use the supplied check time when available."""
    account = _new_account()
    checked_at = datetime(2026, 1, 1, tzinfo=timezone.utc)

    account.mark_cookie_validation(True, checked_at)

    assert account.is_valid is True
    assert account.last_check_at == checked_at
    assert account.is_listening_candidate()
