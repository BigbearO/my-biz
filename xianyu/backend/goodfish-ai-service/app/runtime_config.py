from pathlib import Path
from typing import Any

import yaml

from .constants import CONFIG_FORMAT_PROPERTIES, CONFIG_FORMAT_YAML
from .errors import ConfigFormatError
from .utils.env_placeholder import expand_placeholders


def parse_config(content: str, config_format: str) -> dict[str, Any]:
    if config_format == CONFIG_FORMAT_YAML:
        return _parse_yaml(content)
    if config_format == CONFIG_FORMAT_PROPERTIES:
        return _parse_properties(content)
    raise ConfigFormatError(f"Unsupported config format: {config_format}")


def load_local_config(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    try:
        content = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise ConfigFormatError(f"Cannot read local config {path}") from exc
    return _parse_yaml(content)


def deep_merge(base: dict[str, Any], override: dict[str, Any]) -> dict[str, Any]:
    """Return a deep union where values in override replace base values."""
    merged = dict(base)
    for key, value in override.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = deep_merge(merged[key], value)
        else:
            merged[key] = value
    return merged


class RuntimeConfigStore:
    def __init__(self, local_config: dict[str, Any]) -> None:
        self._local_config = expand_placeholders(local_config)
        self._config = dict(self._local_config)

    @property
    def config(self) -> dict[str, Any]:
        return dict(self._config)

    def apply_remote(self, content: str, config_format: str) -> dict[str, Any]:
        remote_config = expand_placeholders(parse_config(content, config_format))
        next_config = deep_merge(self._local_config, remote_config)
        self._config = next_config
        return self.config


def _parse_yaml(content: str) -> dict[str, Any]:
    if not content.strip():
        return {}
    try:
        parsed = yaml.safe_load(content)
    except yaml.YAMLError as exc:
        raise ConfigFormatError("Invalid YAML configuration") from exc
    if parsed is None:
        return {}
    if not isinstance(parsed, dict):
        raise ConfigFormatError("YAML configuration must be a mapping")
    return parsed


def _parse_properties(content: str) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for raw_line in content.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        key, value = _split_properties_line(line)
        _set_nested_value(result, _split_property_key(key), value)
    return result


def _split_properties_line(line: str) -> tuple[str, str]:
    for index, char in enumerate(line):
        if char in {"=", ":"} and (index == 0 or line[index - 1] != "\\"):
            return line[:index].strip(), line[index + 1 :].strip()
    parts = line.split(None, 1)
    if len(parts) == 1:
        return parts[0], ""
    return parts[0].strip(), parts[1].strip()


def _split_property_key(key: str) -> list[str]:
    return [part for part in key.replace("\\.", "\x00").split(".") if part]


def _set_nested_value(target: dict[str, Any], keys: list[str], value: str) -> None:
    if not keys:
        raise ConfigFormatError("Properties configuration contains an empty key")
    current = target
    for key in keys[:-1]:
        normalized_key = key.replace("\x00", ".")
        next_value = current.get(normalized_key)
        if not isinstance(next_value, dict):
            next_value = {}
            current[normalized_key] = next_value
        current = next_value
    normalized_key = keys[-1].replace("\x00", ".")
    current[normalized_key] = value
