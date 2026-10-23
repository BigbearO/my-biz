"""Request/response schemas for POST /api/ai/reply."""

from pydantic import BaseModel, Field, field_validator


class ReplyRequest(BaseModel):
    request_id: str = Field(min_length=1, max_length=64)
    goodfish_item_id: str = Field(min_length=1, max_length=64)
    buyer_id: str = Field(min_length=1, max_length=64)
    goodfish_account_id: str = Field(min_length=1, max_length=64)
    message: str = Field(min_length=1)
    # Xianyu's original field: passed through when the client can parse it,
    # never used for routing. Absent means an empty string.
    msg_type: str = ""
    # Either a single URL or an array; normalized to a list before validation.
    image_url: list[str] | None = None

    @field_validator("message")
    @classmethod
    def validate_message(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("message must not be blank")
        return value

    @field_validator("image_url", mode="before")
    @classmethod
    def normalize_image_url(cls, value: object) -> list[str] | None:
        if value is None:
            return None
        if isinstance(value, str):
            return [value] if value else None
        if isinstance(value, list):
            return [item for item in value if item]
        return None

    @property
    def image_urls(self) -> list[str]:
        return self.image_url or []


class ReplyData(BaseModel):
    reply: str
    conversation_id: str
    model_name: str
    latency_ms: int


class ReplyResponse(BaseModel):
    code: int
    message: str
    data: ReplyData | None = None
