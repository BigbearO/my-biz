from xianyu_client.core.constants import DATABASE_FILE_NAME
from xianyu_client.core.settings import load_settings


def test_load_settings_creates_runtime_directories(tmp_path):
    """Runtime directories are created before the local service opens files."""
    settings = load_settings({
        "GOODFISH_DATA_DIR": str(tmp_path),
        "GOODFISH_LOCAL_TOKEN": "local-token",
        "GOODFISH_LOG_LEVEL": "debug",
        "GOODFISH_ENV": "development",
    })

    assert settings.data_dir == tmp_path.resolve()
    assert settings.local_token == "local-token"
    assert settings.log_level == "DEBUG"
    assert settings.database_path == tmp_path / "data" / DATABASE_FILE_NAME
    assert settings.database_path.parent.is_dir()
    assert settings.log_dir.is_dir()

