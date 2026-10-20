"""Integration test for Electron parent-pipe shutdown."""

import json
import os
import secrets
import subprocess
import sys
import tempfile
from pathlib import Path

from xianyu_client.core.constants import (
    ENV_DATA_DIR,
    ENV_LOCAL_TOKEN,
    ENV_PARENT_PIPE,
    HANDSHAKE_BEGIN,
    HANDSHAKE_END,
    LOCAL_TOKEN_BYTE_LENGTH,
    PARENT_PIPE_ENABLED_VALUE,
    SERVER_SHUTDOWN_TIMEOUT_SECONDS,
)


def _parse_handshake(line: str) -> dict:
    """Extract the local-service handshake payload from one stdout line."""
    body_start = line.index(HANDSHAKE_BEGIN) + len(HANDSHAKE_BEGIN)
    body_end = line.index(HANDSHAKE_END, body_start)
    return json.loads(line[body_start:body_end])


def test_parent_pipe_eof_stops_local_service():
    """Closing Electron's stdin pipe stops the local service without taskkill."""
    local_token = secrets.token_urlsafe(LOCAL_TOKEN_BYTE_LENGTH)
    service_directory = Path(__file__).parents[1]

    with tempfile.TemporaryDirectory(prefix="goodfish-parent-pipe-") as data_directory:
        environment = os.environ.copy()
        environment[ENV_DATA_DIR] = data_directory
        environment[ENV_LOCAL_TOKEN] = local_token
        environment[ENV_PARENT_PIPE] = PARENT_PIPE_ENABLED_VALUE
        process = subprocess.Popen(
            [sys.executable, "-m", "xianyu_client"],
            cwd=service_directory,
            env=environment,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
        )
        try:
            payload = _parse_handshake(process.stdout.readline())
            assert payload["port"] > 0
            process.stdin.close()
            process.wait(timeout=SERVER_SHUTDOWN_TIMEOUT_SECONDS)
            assert process.returncode == 0
        finally:
            if process.poll() is None:
                process.kill()
            process.wait(timeout=SERVER_SHUTDOWN_TIMEOUT_SECONDS)
