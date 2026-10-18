"""Response schemas for local system APIs."""

from pydantic import BaseModel


class SystemStatusResponse(BaseModel):
    """Runtime status returned to the Electron renderer."""

    version: str
    serviceState: str
    listenCount: int
    cloudState: str
    authState: str
    credentialState: str

