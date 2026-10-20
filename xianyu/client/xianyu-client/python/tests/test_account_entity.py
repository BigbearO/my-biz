"""Tests for behavior concentrated on the Goodfish account entity."""

from datetime import datetime, timezone

import pytest

from xianyu_client.core.constants import (
    ACCOUNT_INITIAL_IS_VALID,
    ACCOUNT_INITIAL_REMARK,
    ACCOUNT_LISTEN_STATE_LISTENING,
    ACCOUNT_LISTEN_STATE_IDLE,
)
from xianyu_client.core.errors import AccountStateConflictError
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


def test_create_local_supplies_safe_defaults():
    """The entity factory creates an idle account awaiting cookie validation."""
    account = GoodfishAccountModel.create_local(
        goodfish_id=TEST_GOODFISH_ID,
        goodfish_nickname=TEST_GOODFISH_NICKNAME,
        encrypted_cookie=TEST_ENCRYPTED_COOKIE,
    )

    assert account.remark == ACCOUNT_INITIAL_REMARK
    assert account.is_valid is ACCOUNT_INITIAL_IS_VALID
    assert account.listen_state == ACCOUNT_LISTEN_STATE_IDLE


def test_update_profile_only_changes_editable_fields():
    """Profile updates leave cookie and listening state untouched."""
    account = _new_account()

    account.update_profile("buyer-two", "operator note")

    assert account.goodfish_nickname == "buyer-two"
    assert account.remark == "operator note"
    assert account.cookie == TEST_ENCRYPTED_COOKIE
    assert account.listen_state == ACCOUNT_LISTEN_STATE_IDLE


def test_replace_cookie_resets_validation_state():
    """Cookie replacement forces a later validation pass before listening."""
    account = _new_account()
    account.mark_cookie_validation(True)
    replacement_cookie = "cmVwbGFjZWQtY3J5cHRvZGF0YQ=="

    account.replace_cookie(replacement_cookie)

    assert account.cookie == replacement_cookie
    assert account.is_valid is False
    assert not account.is_listening_candidate()


def test_replace_verified_cookie_refreshes_profile_and_validation_state():
    """A replacement cookie that passes verification becomes immediately usable."""
    account = _new_account()
    replacement_cookie = "cmVwbGFjZWQtdmVyaWZpZWQtY3J5cHRvZGF0YQ=="
    verified_nickname = "buyer-verified"

    account.replace_verified_cookie(replacement_cookie, verified_nickname)

    assert account.cookie == replacement_cookie
    assert account.goodfish_nickname == verified_nickname
    assert account.is_valid is True
    assert account.is_listening_candidate()


def test_non_idle_account_rejects_cookie_change_and_deletion():
    """Listener-managed accounts cannot have credentials removed or replaced."""
    account = _new_account()
    account.listen_state = ACCOUNT_LISTEN_STATE_LISTENING

    with pytest.raises(AccountStateConflictError):
        account.ensure_cookie_change_allowed()
    with pytest.raises(AccountStateConflictError):
        account.ensure_deletable()
