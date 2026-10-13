import pytest
from pydantic import ValidationError

from app.bootstrap import BootstrapSettings


def create_settings(**overrides: object) -> BootstrapSettings:
    values = {
        "nacos_server_addr": "nacos.example.com:8848",
        "nacos_namespace_id": "existing-namespace",
        "nacos_service_name": "custom-service",
    }
    values.update(overrides)
    return BootstrapSettings(_env_file=None, **values)


def test_bootstrap_requires_nacos_connection_fields() -> None:
    with pytest.raises(ValidationError):
        BootstrapSettings(_env_file=None)


def test_bootstrap_allows_empty_data_id_and_normalizes_format() -> None:
    settings = create_settings(
        nacos_data_id=" ",
        nacos_config_format="YAML",
    )

    assert settings.nacos_data_id == ""
    assert settings.nacos_config_format == "yaml"
    assert settings.local_config_path.as_posix() == "config/application.yaml"


def test_bootstrap_rejects_unknown_config_format() -> None:
    with pytest.raises(ValidationError):
        create_settings(nacos_config_format="json")


def test_bootstrap_uses_environment_variable_values(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("NACOS_SERVER_ADDR", "env-nacos:8848")
    monkeypatch.setenv("NACOS_NAMESPACE_ID", "env-namespace")
    monkeypatch.setenv("NACOS_SERVICE_NAME", "env-service")
    monkeypatch.setenv("NACOS_DATA_ID", "")

    settings = BootstrapSettings(_env_file=None)

    assert settings.nacos_server_addr == "env-nacos:8848"
    assert settings.nacos_namespace_id == "env-namespace"
    assert settings.nacos_service_name == "env-service"
    assert settings.nacos_data_id == ""
