"""Process-level startup tests for the local desktop service."""

import json
import os
import secrets
import signal
import sqlite3
import subprocess
import sys
import tempfile
from pathlib import Path

import httpx

from xianyu_client.core.constants import (
    ACCOUNT_TABLE_NAME,
    CREDENTIAL_STATE_READY,
    CREDENTIAL_STATE_UNDECRYPTABLE,
    DATABASE_FILE_NAME,
    DATA_DIRECTORY_NAME,
    ENV_DATA_DIR,
    ENV_LOCAL_TOKEN,
    HANDSHAKE_BEGIN,
    HANDSHAKE_END,
    LOCAL_LOG_FILE_NAME,
    LOCAL_TOKEN_BYTE_LENGTH,
    LOCAL_TOKEN_HEADER,
    LOG_DIRECTORY_NAME,
    MASTER_KEY_FILE_NAME,
    RUNTIME_FILE_NAME,
    SERVICE_STATE_READY,
    SERVER_SHUTDOWN_TIMEOUT_SECONDS,
    SERVER_STARTED_TIMEOUT_SECONDS,
    WINDOWS_PLATFORM_NAME,
)


def _parse_handshake(line: str) -> dict:
    """Extract the JSON payload from one local-service handshake line.

    Args:
        line: Complete stdout line emitted by `python -m xianyu_client`.

    Returns:
        The runtime port and process metadata.
    """
    body_start = line.index(HANDSHAKE_BEGIN) + len(HANDSHAKE_BEGIN)
    body_end = line.index(HANDSHAKE_END, body_start)
    return json.loads(line[body_start:body_end])


def test_real_service_startup_handshake_and_status():
    """The real process serves authenticated status and never logs its token."""
    local_token = secrets.token_urlsafe(LOCAL_TOKEN_BYTE_LENGTH)
    service_directory = Path(__file__).parents[1]

    with tempfile.TemporaryDirectory(prefix="goodfish-stage2-") as data_directory:
        environment = os.environ.copy()
        environment[ENV_DATA_DIR] = data_directory
        environment[ENV_LOCAL_TOKEN] = local_token
        process = subprocess.Popen(
            [sys.executable, "-m", "xianyu_client"],
            cwd=service_directory,
            env=environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
        )
        runtime_pid = process.pid
        try:
            handshake = _parse_handshake(process.stdout.readline())
            runtime_pid = handshake["pid"]
            response = httpx.get(
                f"http://127.0.0.1:{handshake['port']}/api/system/status",
                headers={LOCAL_TOKEN_HEADER: local_token},
                timeout=SERVER_STARTED_TIMEOUT_SECONDS,
            )

            assert response.status_code == 200
            payload = response.json()
            assert payload["serviceState"] == SERVICE_STATE_READY
            assert payload["credentialState"] in {CREDENTIAL_STATE_READY, CREDENTIAL_STATE_UNDECRYPTABLE}
            assert (Path(data_directory) / RUNTIME_FILE_NAME).is_file()
            database_path = Path(data_directory) / DATA_DIRECTORY_NAME / DATABASE_FILE_NAME
            assert database_path.is_file()
            expected_credential_state = (
                CREDENTIAL_STATE_READY
                if (Path(data_directory) / MASTER_KEY_FILE_NAME).is_file()
                else CREDENTIAL_STATE_UNDECRYPTABLE
            )
            assert payload["credentialState"] == expected_credential_state
            connection = sqlite3.connect(database_path)
            try:
                tables = {
                    row[0]
                    for row in connection.execute("SELECT name FROM sqlite_master WHERE type = 'table'")
                }
            finally:
                connection.close()
            assert ACCOUNT_TABLE_NAME in tables
        finally:
            if sys.platform == WINDOWS_PLATFORM_NAME:
                os.kill(runtime_pid, signal.SIGTERM)
            process.terminate()
            try:
                process.wait(timeout=SERVER_SHUTDOWN_TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                pass
            if process.poll() is None:
                process.kill()
            process.wait(timeout=SERVER_SHUTDOWN_TIMEOUT_SECONDS)
            assert process.returncode is not None

        log_path = Path(data_directory) / LOG_DIRECTORY_NAME / LOCAL_LOG_FILE_NAME
        if log_path.exists():
            assert local_token not in log_path.read_text(encoding="utf-8")
