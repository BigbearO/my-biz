import asyncio
import json
import logging
from collections.abc import Callable, Sequence
from typing import Any, Generic, TypeVar

import grpc

from .config import GrpcChannelSettings, GrpcDiscoverySettings
from .errors import ServiceUnavailableError
from .naming import healthy_instance_addresses

logger = logging.getLogger(__name__)

T = TypeVar("T")


class ManagedChannel(Generic[T]):
    def __init__(
        self,
        alias: str,
        service_name: str,
        discovery: GrpcDiscoverySettings,
        channel_settings: GrpcChannelSettings,
        interceptors: Sequence[Any] | None = None,
    ) -> None:
        self._alias = alias
        self._service_name = service_name
        self._discovery = discovery
        self._channel_settings = channel_settings
        self._interceptors = list(interceptors or ())
        self._channel: grpc.aio.Channel | None = None
        self._addresses: tuple[str, ...] = ()
        self._stub: T | None = None
        self._stub_channel: grpc.aio.Channel | None = None
        self._rebuild_task: asyncio.Task[None] | None = None
        self._close_tasks: set[asyncio.Task[None]] = set()
        self._created_channels: set[grpc.aio.Channel] = set()
        self._lock = asyncio.Lock()

    @property
    def service_name(self) -> str:
        return self._service_name

    @property
    def addresses(self) -> tuple[str, ...]:
        return self._addresses

    def on_instances_change(self, instances: Sequence[Any]) -> None:
        addresses = healthy_instance_addresses(instances)
        if self._rebuild_task is not None and not self._rebuild_task.done():
            self._rebuild_task.cancel()
        self._rebuild_task = asyncio.create_task(self._rebuild_after_debounce(addresses))

    async def refresh_instances(self, instances: Sequence[Any]) -> None:
        await self._rebuild(healthy_instance_addresses(instances))

    async def update_settings(
        self,
        discovery: GrpcDiscoverySettings,
        channel_settings: GrpcChannelSettings,
    ) -> None:
        async with self._lock:
            self._discovery = discovery
            self._channel_settings = channel_settings
            self._addresses = ()

    def channel(self) -> grpc.aio.Channel:
        if self._channel is None:
            raise ServiceUnavailableError(
                f"No available gRPC instance: service={self._service_name}"
            )
        return self._channel

    def stub(self, factory: Callable[[grpc.aio.Channel], T]) -> T:
        channel = self.channel()
        if self._stub is None or self._stub_channel is not channel:
            self._stub = factory(channel)
            self._stub_channel = channel
        return self._stub

    async def close(self) -> None:
        if self._rebuild_task is not None and not self._rebuild_task.done():
            self._rebuild_task.cancel()
        close_tasks = list(self._close_tasks)
        for close_task in close_tasks:
            close_task.cancel()
        for channel in self._created_channels:
            try:
                await channel.close()
            except Exception:
                logger.exception(
                    "Failed to close gRPC channel: alias=%s service=%s",
                    self._alias,
                    self._service_name,
                )
        if self._rebuild_task is not None:
            await asyncio.gather(self._rebuild_task, return_exceptions=True)
        if close_tasks:
            await asyncio.gather(*close_tasks, return_exceptions=True)

    async def _rebuild_after_debounce(self, addresses: tuple[str, ...]) -> None:
        delay_seconds = self._discovery.debounce_ms / 1000
        await asyncio.sleep(delay_seconds)
        await self._rebuild(addresses)

    async def _rebuild(self, addresses: tuple[str, ...]) -> None:
        if not addresses:
            logger.warning(
                "gRPC instance list is empty, keep previous channel: alias=%s service=%s",
                self._alias,
                self._service_name,
            )
            return
        if addresses == self._addresses and self._channel is not None:
            return

        async with self._lock:
            if addresses == self._addresses and self._channel is not None:
                return
            previous_channel = self._channel
            self._channel = self._build(addresses)
            self._created_channels.add(self._channel)
            self._addresses = addresses

        if previous_channel is not None:
            self._schedule_close(previous_channel)
        logger.info(
            "gRPC channel updated: alias=%s service=%s targets=%s",
            self._alias,
            self._service_name,
            ",".join(addresses),
        )

    def _build(self, addresses: tuple[str, ...]) -> grpc.aio.Channel:
        max_message_bytes = self._channel_settings.max_message_mb * 1024 * 1024
        options: list[tuple[str, object]] = [
            ("grpc.keepalive_time_ms", self._channel_settings.keepalive_time_ms),
            ("grpc.keepalive_timeout_ms", self._channel_settings.keepalive_timeout_ms),
            (
                "grpc.keepalive_permit_without_calls",
                self._channel_settings.keepalive_permit_without_calls,
            ),
            ("grpc.http2.max_pings_without_data", 0),
            ("grpc.max_receive_message_length", max_message_bytes),
            ("grpc.max_send_message_length", max_message_bytes),
        ]
        if self._channel_settings.health_check:
            service_config = {
                "loadBalancingConfig": [{self._channel_settings.lb_policy: {}}],
                "healthCheckConfig": {"serviceName": ""},
            }
            options.append(("grpc.service_config", json.dumps(service_config)))
        else:
            options.append(("grpc.lb_policy_name", self._channel_settings.lb_policy))

        target = "ipv4:" + ",".join(addresses)
        return grpc.aio.insecure_channel(
            target=target,
            options=options,
            interceptors=self._interceptors,
        )

    def _schedule_close(self, channel: grpc.aio.Channel) -> None:
        close_task = asyncio.create_task(self._close_later(channel))
        self._close_tasks.add(close_task)
        close_task.add_done_callback(self._close_tasks.discard)

    async def _close_later(self, channel: grpc.aio.Channel) -> None:
        await asyncio.sleep(self._discovery.close_grace_seconds)
        try:
            await channel.close()
        except Exception:
            logger.exception(
                "Failed to close previous gRPC channel: alias=%s service=%s",
                self._alias,
                self._service_name,
            )
        finally:
            self._created_channels.discard(channel)
