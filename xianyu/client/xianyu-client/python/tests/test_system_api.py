import httpx
import sqlite3

from xianyu_client.core.constants import MASTER_KEY_FILE_NAME
from xianyu_client.app import create_app
from xianyu_client.core.settings import load_settings
from xianyu_client.db.models import GoodfishAccountModel
from tests.helpers.key_protectors import ReversibleKeyProtector


async def test_system_status_requires_local_token(tmp_path):
    """The local status endpoint rejects missing and invalid token values."""
    settings = load_settings({
        "GOODFISH_DATA_DIR": str(tmp_path),
        "GOODFISH_LOCAL_TOKEN": "expected-token",
    })
    application = create_app(settings, ReversibleKeyProtector())
    transport = httpx.ASGITransport(app=application)

    async with application.router.lifespan_context(application):
        async with transport, httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as client:
            missing = await client.get("/api/system/status")
            invalid = await client.get("/api/system/status", headers={"X-Local-Token": "wrong"})
            valid = await client.get("/api/system/status", headers={"X-Local-Token": "expected-token"})

    assert missing.status_code == 401
    assert invalid.status_code == 401
    assert valid.status_code == 200
    assert valid.json() == {
        "version": "0.1.0",
        "serviceState": "ready",
        "listenCount": 0,
        "cloudState": "not_configured",
        "authState": "not_logged_in",
        "credentialState": "ready",
    }
    assert (tmp_path / MASTER_KEY_FILE_NAME).is_file()
    connection = sqlite3.connect(settings.database_path)
    try:
        table_names = {
            row[0]
            for row in connection.execute("SELECT name FROM sqlite_master WHERE type = 'table'")
        }
    finally:
        connection.close()
    assert GoodfishAccountModel.__tablename__ in table_names

