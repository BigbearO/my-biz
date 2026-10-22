"""Conversions between account entities and transport schemas."""

from xianyu_client.db.models import GoodfishAccountModel
from xianyu_client.schemas.account_schemas import AccountDetailResponse, AccountResponse


class AccountConvert:
    """Stateless converter for account transport representations."""

    @staticmethod
    def to_response(account: GoodfishAccountModel) -> AccountResponse:
        """Convert one account entity without exposing cookie material.

        Args:
            account: Persisted local account entity.

        Returns:
            The non-secret API response representation.
        """
        return AccountResponse(
            id=account.id,
            goodfish_id=account.goodfish_id,
            goodfish_nickname=account.goodfish_nickname,
            is_valid=account.is_valid,
            listen_state=account.listen_state,
            remark=account.remark,
            last_check_at=account.last_check_at,
            created_at=account.created_at,
            updated_at=account.updated_at,
        )

    @staticmethod
    def to_detail_response(account: GoodfishAccountModel, cookie: str) -> AccountDetailResponse:
        """Convert one account entity including its decrypted cookie.

        Args:
            account: Persisted local account entity.
            cookie: Plaintext cookie decrypted for the detail dialog only.

        Returns:
            The detail API representation.
        """
        return AccountDetailResponse(
            id=account.id,
            goodfish_id=account.goodfish_id,
            goodfish_nickname=account.goodfish_nickname,
            is_valid=account.is_valid,
            listen_state=account.listen_state,
            remark=account.remark,
            last_check_at=account.last_check_at,
            created_at=account.created_at,
            updated_at=account.updated_at,
            cookie=cookie,
        )
