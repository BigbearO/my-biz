"""Request and response schemas for local login state APIs."""

from pydantic import BaseModel, ConfigDict, Field

from xianyu_client.core.constants import (
    AUTH_CLIENT_ID_MAX_LENGTH,
    AUTH_TOKEN_MAX_LENGTH,
)


class LoginRequest(BaseModel):
    """Token pair handed over by the Electron login flow."""

    model_config = ConfigDict(
        populate_by_name=True,
        str_strip_whitespace=True,
        extra="forbid",
    )

    token: str = Field(min_length=1, max_length=AUTH_TOKEN_MAX_LENGTH)
    client_id: str = Field(
        validation_alias="clientid",
        min_length=1,
        max_length=AUTH_CLIENT_ID_MAX_LENGTH,
    )


class TokenResponse(BaseModel):
    """Stored token pair returned to the renderer."""

    model_config = ConfigDict(populate_by_name=True)

    token: str
    client_id: str = Field(serialization_alias="clientid")


class AuthStateResponse(BaseModel):
    """Authorization state after a login, logout, or refresh action."""

    auth_state: str = Field(serialization_alias="authState")
