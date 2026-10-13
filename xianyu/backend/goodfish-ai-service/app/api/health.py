from fastapi import Request

from ..constants import HEALTH_STATUS_UP


async def health(request: Request) -> dict[str, str]:
    bootstrap = request.app.state.bootstrap
    return {
        "status": HEALTH_STATUS_UP,
        "env": bootstrap.app_env,
        "service": bootstrap.nacos_service_name,
        "version": request.app.state.service_version,
    }
