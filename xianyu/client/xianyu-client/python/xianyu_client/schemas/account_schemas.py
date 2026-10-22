"""Request and response schemas for local account APIs."""

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field

from xianyu_client.core.constants import (
    ACCOUNT_COOKIE_MAX_LENGTH,
    ACCOUNT_GOODFISH_NICKNAME_MAX_LENGTH,
    ACCOUNT_INITIAL_REMARK,
    ACCOUNT_REMARK_MAX_LENGTH,
)


class AccountCreateRequest(BaseModel):
    """Payload for creating one local Goodfish account."""

    model_config = ConfigDict(
        populate_by_name=True,
        str_strip_whitespace=True,
        extra="forbid",
    )

    cookie: str = Field(min_length=1, max_length=ACCOUNT_COOKIE_MAX_LENGTH)
    remark: str = Field(default=ACCOUNT_INITIAL_REMARK, max_length=ACCOUNT_REMARK_MAX_LENGTH)


class AccountUpdateRequest(BaseModel):
    """Payload for editing profile fields and optionally replacing a cookie."""

    model_config = ConfigDict(
        populate_by_name=True,
        str_strip_whitespace=True,
        extra="forbid",
    )

    goodfish_nickname: str = Field(
        validation_alias="nickname",
        min_length=1,
        max_length=ACCOUNT_GOODFISH_NICKNAME_MAX_LENGTH,
    )
    remark: str = Field(max_length=ACCOUNT_REMARK_MAX_LENGTH)
    cookie: Optional[str] = Field(
        default=None,
        min_length=1,
        max_length=ACCOUNT_COOKIE_MAX_LENGTH,
    )


class AccountResponse(BaseModel):
    """Non-secret account representation returned to the renderer."""

    model_config = ConfigDict(populate_by_name=True)

    id: int
    goodfish_id: str = Field(serialization_alias="goodfishId")
    goodfish_nickname: str = Field(serialization_alias="nickname")
    is_valid: bool = Field(serialization_alias="isValid")
    listen_state: str = Field(serialization_alias="listenState")
    remark: str
    last_check_at: datetime = Field(serialization_alias="lastCheckAt")
    created_at: datetime = Field(serialization_alias="createdAt")
    updated_at: datetime = Field(serialization_alias="updatedAt")


class AccountDetailResponse(AccountResponse):
    """Account representation that includes the decrypted cookie for the detail dialog."""

    cookie: str
