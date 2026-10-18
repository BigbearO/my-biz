"""Tests for DPAPI-backed master-key lifecycle management."""

import pytest

from xianyu_client.core.constants import MASTER_KEY_LENGTH_BYTES
from xianyu_client.core.dpapi import DpapiError, WindowsDpapiKeyProtector
from xianyu_client.managers.master_key_manager import MasterKeyDecryptionError, MasterKeyManager
from tests.helpers.key_protectors import PROTECTED_KEY_PREFIX, ReversibleKeyProtector


CORRUPTED_KEY_BYTES = b"corrupted-master-key"
INVALID_RESTORED_KEY_LENGTH = 1
DPAPI_PROFILE_NOT_LOADED_ERROR_CODE = 2


async def test_load_or_create_creates_one_protected_key(tmp_path):
    """A new key is persisted once and restored unchanged on later calls."""
    key_path = tmp_path / "master.key"
    manager = MasterKeyManager(key_path, ReversibleKeyProtector())

    first_key = await manager.load_or_create()
    protected_key = key_path.read_bytes()
    second_key = await manager.load_or_create()

    assert len(first_key) == MASTER_KEY_LENGTH_BYTES
    assert first_key == second_key
    assert protected_key.startswith(PROTECTED_KEY_PREFIX)
    assert protected_key[len(PROTECTED_KEY_PREFIX):] == first_key


async def test_existing_corrupt_key_is_not_overwritten(tmp_path):
    """A corrupt master-key file is preserved for manual recovery."""
    key_path = tmp_path / "master.key"
    key_path.write_bytes(CORRUPTED_KEY_BYTES)
    manager = MasterKeyManager(key_path, ReversibleKeyProtector())

    with pytest.raises(MasterKeyDecryptionError):
        await manager.load_or_create()

    assert key_path.read_bytes() == CORRUPTED_KEY_BYTES
    assert [path.name for path in tmp_path.iterdir()] == [key_path.name]


async def test_existing_key_with_invalid_length_is_not_overwritten(tmp_path):
    """An existing protected key that restores to the wrong length is rejected."""
    key_path = tmp_path / "master.key"
    original_key = PROTECTED_KEY_PREFIX + bytes(INVALID_RESTORED_KEY_LENGTH)
    key_path.write_bytes(original_key)
    manager = MasterKeyManager(key_path, ReversibleKeyProtector())

    with pytest.raises(MasterKeyDecryptionError):
        await manager.load_or_create()

    assert key_path.read_bytes() == original_key


def test_windows_dpapi_current_user_round_trip():
    """The production protector restores a key protected by current-user DPAPI."""
    protector = WindowsDpapiKeyProtector()
    raw_key = bytes(MASTER_KEY_LENGTH_BYTES)

    try:
        protected_key = protector.protect(raw_key)
        restored_key = protector.unprotect(protected_key)
    except DpapiError as error:
        if str(DPAPI_PROFILE_NOT_LOADED_ERROR_CODE) in str(error):
            pytest.skip("Windows user profile is not loaded in this execution context")
        raise

    assert restored_key == raw_key
