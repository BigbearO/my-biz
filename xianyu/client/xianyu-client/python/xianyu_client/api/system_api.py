"""Local HTTP routes for system state."""

from fastapi import APIRouter, Depends

from xianyu_client.api.dependencies import get_system_service, validate_local_token
from xianyu_client.schemas.system_schemas import SystemStatusResponse
from xianyu_client.services.system_service import SystemService

router = APIRouter(prefix="/api/system", dependencies=[Depends(validate_local_token)])


@router.get("/status", response_model=SystemStatusResponse)
async def get_system_status(
    system_service: SystemService = Depends(get_system_service),
) -> SystemStatusResponse:
    """Return local service status for the renderer status panel."""
    return await system_service.get_status()

