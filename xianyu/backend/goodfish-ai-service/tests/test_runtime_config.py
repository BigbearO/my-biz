import pytest

from app.errors import ConfigFormatError
from app.runtime_config import RuntimeConfigStore, deep_merge, parse_config


def test_yaml_and_local_remote_union() -> None:
    local_config = {
        "service": {"name": "local", "local_only": True},
        "timeout": 1,
    }
    remote_config = parse_config(
        """
service:
  name: remote
  remote_only: true
""",
        "yaml",
    )

    merged = deep_merge(local_config, remote_config)

    assert merged == {
        "service": {
            "name": "remote",
            "local_only": True,
            "remote_only": True,
        },
        "timeout": 1,
    }


def test_store_expands_placeholders_and_applies_remote_update(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SERVICE_NAME", "custom-service")
    store = RuntimeConfigStore({"service": {"name": "${SERVICE_NAME}"}})

    assert store.config == {"service": {"name": "custom-service"}}

    store.apply_remote(
        """
service:
  timeout: 3
""",
        "yaml",
    )
    assert store.config == {
        "service": {"name": "custom-service", "timeout": 3},
    }


def test_store_keeps_previous_config_when_remote_content_is_invalid() -> None:
    store = RuntimeConfigStore({"service": {"name": "local"}})

    with pytest.raises(ConfigFormatError):
        store.apply_remote("- invalid\n  mapping: true", "yaml")

    assert store.config == {"service": {"name": "local"}}


def test_store_keeps_previous_config_when_validation_fails() -> None:
    store = RuntimeConfigStore({"service": {"name": "local"}})

    def reject_config(config: dict) -> None:
        raise ValueError(config["grpc"]["channel"]["lb_policy"])

    with pytest.raises(ValueError):
        store.apply_remote(
            """
grpc:
  channel:
    lb_policy: random
""",
            "yaml",
            validate=reject_config,
        )

    assert store.config == {"service": {"name": "local"}}


def test_properties_parser_builds_nested_mapping() -> None:
    config = parse_config(
        """
# comment
service.name=custom-service
service.timeout=3
escaped.key=value
""",
        "properties",
    )

    assert config == {
        "service": {"name": "custom-service", "timeout": "3"},
        "escaped.key": "value",
    }
