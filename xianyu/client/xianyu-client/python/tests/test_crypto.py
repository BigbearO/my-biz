"""Tests for the AES-256-GCM cookie storage primitive."""

import base64
import secrets

import pytest

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from xianyu_client.core.constants import COOKIE_ASSOCIATED_DATA, COOKIE_NONCE_LENGTH_BYTES
from xianyu_client.core.constants import MASTER_KEY_LENGTH_BYTES
from xianyu_client.core.crypto import (
    CookieDecryptionError,
    CookieEncryptionError,
    InvalidCookieKeyError,
    decrypt_cookie,
    encrypt_cookie,
    validate_master_key,
)


TEST_COOKIE = "unb=64; cookie2=token-value; track_id=test-track"
SECOND_TEST_COOKIE = "unb=other; cookie2=other-value"
TAMPER_FLIP_MASK = 0x01
INVALID_BASE64_CHARACTER = "!"


def _new_master_key() -> bytes:
    """Return a randomly generated valid AES-256 key.

    Returns:
        Thirty-two random key bytes.
    """
    return secrets.token_bytes(MASTER_KEY_LENGTH_BYTES)


def _tamper_middle(encrypted_cookie: str) -> str:
    """Flip one ciphertext byte without changing the nonce or tag.

    Args:
        encrypted_cookie: Valid base64 cookie ciphertext.

    Returns:
        Tampered base64 cookie ciphertext.
    """
    payload = bytearray(base64.b64decode(encrypted_cookie))
    middle_index = len(payload) // 2
    payload[middle_index] ^= TAMPER_FLIP_MASK
    return base64.b64encode(payload).decode("ascii")


def test_encrypt_cookie_round_trips_with_the_same_key():
    """Encryption and decryption restore the original cookie exactly."""
    master_key = _new_master_key()

    encrypted_cookie = encrypt_cookie(master_key, TEST_COOKIE)

    assert encrypted_cookie != TEST_COOKIE
    assert decrypt_cookie(master_key, encrypted_cookie) == TEST_COOKIE


def test_encrypt_cookie_uses_a_random_nonce():
    """Repeated encryption of one cookie produces distinct ciphertexts."""
    master_key = _new_master_key()

    first_cookie = encrypt_cookie(master_key, TEST_COOKIE)
    second_cookie = encrypt_cookie(master_key, TEST_COOKIE)

    assert first_cookie != second_cookie
    assert decrypt_cookie(master_key, first_cookie) == TEST_COOKIE
    assert decrypt_cookie(master_key, second_cookie) == TEST_COOKIE


def test_encrypt_cookie_rejects_an_empty_cookie():
    """Encryption rejects an empty plaintext cookie."""
    master_key = _new_master_key()

    with pytest.raises(CookieEncryptionError):
        encrypt_cookie(master_key, "")


def test_encrypt_cookie_rejects_an_invalid_key():
    """Encryption rejects a master key before touching cookie content."""
    invalid_key = b""

    with pytest.raises(InvalidCookieKeyError):
        encrypt_cookie(invalid_key, TEST_COOKIE)


@pytest.mark.parametrize("key_length", [0, 1, 31, 33])
def test_validate_master_key_rejects_wrong_lengths(key_length):
    """Master-key validation accepts only the AES-256 key length."""
    invalid_key = secrets.token_bytes(key_length)

    with pytest.raises(InvalidCookieKeyError):
        validate_master_key(invalid_key)


def test_decrypt_cookie_rejects_an_empty_value():
    """An absent ciphertext cannot be interpreted as a valid cookie."""
    master_key = _new_master_key()

    with pytest.raises(CookieDecryptionError):
        decrypt_cookie(master_key, "")


def test_decrypt_cookie_rejects_invalid_base64():
    """Malformed storage text fails authentication before plaintext handling."""
    master_key = _new_master_key()

    with pytest.raises(CookieDecryptionError):
        decrypt_cookie(master_key, INVALID_BASE64_CHARACTER)


def test_decrypt_cookie_rejects_tampered_nonce_ciphertext_and_tag():
    """Any protected payload segment changing causes authentication failure."""
    master_key = _new_master_key()
    encrypted_cookie = encrypt_cookie(master_key, TEST_COOKIE)
    payload = bytearray(base64.b64decode(encrypted_cookie))

    nonce_index = 0
    ciphertext_index = len(payload) // 2
    tag_index = len(payload) - 1
    for tampered_index in (nonce_index, ciphertext_index, tag_index):
        payload[tampered_index] ^= TAMPER_FLIP_MASK
        tampered_cookie = base64.b64encode(payload).decode("ascii")
        with pytest.raises(CookieDecryptionError):
            decrypt_cookie(master_key, tampered_cookie)
        payload[tampered_index] ^= TAMPER_FLIP_MASK


def test_decrypt_cookie_rejects_the_wrong_key():
    """A different valid master key cannot authenticate stored ciphertext."""
    encryption_key = _new_master_key()
    decryption_key = _new_master_key()
    encrypted_cookie = encrypt_cookie(encryption_key, TEST_COOKIE)

    with pytest.raises(CookieDecryptionError):
        decrypt_cookie(decryption_key, encrypted_cookie)


def test_decrypt_cookie_requires_the_associated_data():
    """Ciphertext created without the fixed associated data is rejected."""
    master_key = _new_master_key()
    nonce = secrets.token_bytes(COOKIE_NONCE_LENGTH_BYTES)
    unbound_ciphertext = AESGCM(master_key).encrypt(nonce, TEST_COOKIE.encode("utf-8"), None)
    encrypted_cookie = base64.b64encode(nonce + unbound_ciphertext).decode("ascii")

    with pytest.raises(CookieDecryptionError):
        decrypt_cookie(master_key, encrypted_cookie)


def test_decrypt_cookie_rejects_a_different_cookie_value():
    """The authenticated payload cannot be swapped with another ciphertext."""
    master_key = _new_master_key()
    encrypted_cookie = encrypt_cookie(master_key, TEST_COOKIE)
    tampered_cookie = _tamper_middle(encrypt_cookie(master_key, SECOND_TEST_COOKIE))

    assert tampered_cookie != encrypted_cookie
    with pytest.raises(CookieDecryptionError):
        decrypt_cookie(master_key, tampered_cookie)
