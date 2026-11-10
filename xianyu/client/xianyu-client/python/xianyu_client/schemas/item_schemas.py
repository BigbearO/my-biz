"""Response schemas for local product synchronisation APIs."""

from pydantic import BaseModel, ConfigDict, Field


class ItemSyncStatusResponse(BaseModel):
    """Synchronisation state of one account's on-sale listing."""

    model_config = ConfigDict(populate_by_name=True)

    account_id: int = Field(serialization_alias="accountId")
    state: str
    total: int
    inserted: int
    updated: int
    unchanged: int
    message: str
