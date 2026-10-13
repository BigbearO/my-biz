import os
import re
from typing import Any

_PLACEHOLDER_PATTERN = re.compile(r"\$\{(?P<name>[A-Za-z_][A-Za-z0-9_]*)\}")


def expand_placeholders(node: Any) -> Any:
    """Expand environment placeholders without changing YAML structure."""
    if isinstance(node, dict):
        return {key: expand_placeholders(value) for key, value in node.items()}
    if isinstance(node, list):
        return [expand_placeholders(value) for value in node]
    if isinstance(node, str):
        return _PLACEHOLDER_PATTERN.sub(
            lambda match: os.environ.get(match.group("name"), match.group(0)),
            node,
        )
    return node
