"""Test key protectors that avoid requiring a loaded Windows user profile."""

from xianyu_client.core.dpapi import DpapiError


PROTECTED_KEY_PREFIX = b"reversible:"
PROTECTION_ERROR = DpapiError("Master key protection is unavailable")


class ReversibleKeyProtector:
    """Applies a deterministic wrapper for local service integration tests."""

    def protect(self, key: bytes) -> bytes:
        """Wrap a raw key so the reverse operation can restore it.

        Args:
            key: Raw key bytes.

        Returns:
            The wrapped key bytes.
        """
        return PROTECTED_KEY_PREFIX + key

    def unprotect(self, protected_key: bytes) -> bytes:
        """Restore a key previously returned by `protect`.

        Args:
            protected_key: Wrapped key bytes.

        Returns:
            The raw key bytes.

        Raises:
            DpapiError: If the input was not produced by this protector.
        """
        if not protected_key.startswith(PROTECTED_KEY_PREFIX):
            raise DpapiError("Protected key format is invalid")
        return protected_key[len(PROTECTED_KEY_PREFIX):]


class UnavailableKeyProtector:
    """Simulates an operating-system key protector that cannot run."""

    def protect(self, key: bytes) -> bytes:
        """Reject key protection without exposing the supplied key.

        Args:
            key: Raw key bytes.

        Raises:
            DpapiError: Always, to simulate protector failure.
        """
        raise PROTECTION_ERROR

    def unprotect(self, protected_key: bytes) -> bytes:
        """Reject key restoration without exposing the stored input.

        Args:
            protected_key: Wrapped key bytes.

        Raises:
            DpapiError: Always, to simulate protector failure.
        """
        raise PROTECTION_ERROR
