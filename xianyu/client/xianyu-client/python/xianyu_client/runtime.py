"""Runtime handshake and metadata persistence."""

import json
import os
from datetime import datetime, timezone
from pathlib import Path

from xianyu_client import APP_VERSION
from xianyu_client.core.constants import RUNTIME_FILE_NAME


def create_runtime_payload(port: int, pid: int) -> dict:
    """Create the JSON payload exchanged through runtime metadata."""
    return {
        "port": port,
        "pid": pid,
        "version": APP_VERSION,
        "startedAt": datetime.now(timezone.utc).isoformat(),
    }


def write_runtime_file(data_dir: Path, port: int, pid: int) -> dict:
    """Atomically write runtime metadata for handshake fallback.

    Args:
        data_dir: Application data directory supplied by Electron.
        port: Actual localhost port assigned by the operating system.
        pid: Local service process identifier.

    Returns:
        The runtime payload written to disk.
    """
    payload = create_runtime_payload(port, pid)
    target = data_dir / RUNTIME_FILE_NAME
    temporary = target.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(temporary, target)
    return payload


def create_handshake_line(payload: dict) -> str:
    """Format the single-line stdout handshake protocol."""
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    return f"#GOODFISH_PORT#{body}#END#"

