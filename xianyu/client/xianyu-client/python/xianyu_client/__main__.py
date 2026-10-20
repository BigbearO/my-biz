"""Command-line entry point for the local desktop service."""

import asyncio
import os
import sys
import threading

import uvicorn

from xianyu_client import APP_VERSION
from xianyu_client.app import create_app
from xianyu_client.core.constants import (
    ENV_PARENT_PIPE,
    PARENT_PIPE_ENABLED_VALUE,
    PARENT_PIPE_MONITOR_THREAD_NAME,
    RANDOM_PORT,
    SERVER_HOST,
    SERVER_SHUTDOWN_TIMEOUT_SECONDS,
    SERVER_STARTED_POLL_INTERVAL_SECONDS,
    SERVER_STARTED_TIMEOUT_SECONDS,
)
from xianyu_client.core.logging import setup_logging
from xianyu_client.core.settings import load_settings
from xianyu_client.runtime import create_handshake_line, write_runtime_file


async def wait_for_server_startup(server: uvicorn.Server) -> None:
    """Wait until Uvicorn has opened its dynamically assigned socket."""
    elapsed = 0.0
    while not server.started:
        if server.should_exit:
            raise RuntimeError("Local service exited before opening its socket")
        if elapsed >= SERVER_STARTED_TIMEOUT_SECONDS:
            raise TimeoutError("Local service startup timed out")
        await asyncio.sleep(SERVER_STARTED_POLL_INTERVAL_SECONDS)
        elapsed += SERVER_STARTED_POLL_INTERVAL_SECONDS


def get_server_port(server: uvicorn.Server) -> int:
    """Read the operating-system assigned port from the active Uvicorn socket."""
    sockets = server.servers[0].sockets if server.servers else []
    if not sockets:
        raise RuntimeError("Local service did not expose a listening socket")
    return int(sockets[0].getsockname()[1])


def monitor_parent_pipe(server: uvicorn.Server) -> None:
    """Stop Uvicorn when Electron closes the parent-owned stdin pipe."""
    try:
        stdin_buffer = sys.stdin.buffer
        stdin_buffer.read(1)
    except (AttributeError, OSError, ValueError):
        pass
    server.should_exit = True


def start_parent_pipe_monitor(server: uvicorn.Server) -> None:
    """Start parent-pipe monitoring only when Electron explicitly enables it."""
    if os.environ.get(ENV_PARENT_PIPE) != PARENT_PIPE_ENABLED_VALUE:
        return
    threading.Thread(
        target=monitor_parent_pipe,
        args=(server,),
        name=PARENT_PIPE_MONITOR_THREAD_NAME,
        daemon=True,
    ).start()


async def run_service() -> None:
    """Start FastAPI on a random localhost port and emit the handshake line."""
    settings = load_settings()
    setup_logging(settings.log_dir, settings.log_level, (settings.local_token,))
    application = create_app(settings)
    server_config = uvicorn.Config(
        application,
        host=SERVER_HOST,
        port=RANDOM_PORT,
        log_config=None,
        access_log=False,
    )
    server = uvicorn.Server(server_config)
    start_parent_pipe_monitor(server)
    server_task = asyncio.create_task(server.serve())
    try:
        await wait_for_server_startup(server)
        port = get_server_port(server)
        payload = write_runtime_file(settings.data_dir, port, os.getpid())
        print(create_handshake_line(payload), flush=True)
        await server_task
    finally:
        server.should_exit = True
        if not server_task.done():
            try:
                await asyncio.wait_for(server_task, timeout=SERVER_SHUTDOWN_TIMEOUT_SECONDS)
            except asyncio.TimeoutError:
                server_task.cancel()
                await asyncio.gather(server_task, return_exceptions=True)
        else:
            await asyncio.gather(server_task, return_exceptions=True)


def main() -> None:
    """Run the local service until Electron disconnects or terminates it."""
    asyncio.run(run_service())


if __name__ == "__main__":
    main()
