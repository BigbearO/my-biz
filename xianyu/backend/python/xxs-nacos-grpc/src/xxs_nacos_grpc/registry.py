import asyncio
import logging
from collections.abc import Sequence
from typing import Any, Callable, Generic, TypeVar

from .channel import ManagedChannel
from .config import GrpcSettings
from .errors import ServiceUnavailableError
from .naming import NacosInstanceSource

logger = logging.getLogger(__name__)

T = TypeVar("T")


class GrpcChannelRegistry(Generic[T]):
    def __init__(
        self,
        settings: GrpcSettings,
        instance_source: NacosInstanceSource,
        interceptors: Sequence[Any] | None = None,
    ) -> None:
        self._settings = settings
        self._instance_source = instance_source
        self._interceptors = list(interceptors or ())
        self._channels = {
            alias: ManagedChannel[T](
                alias=alias,
                service_name=service_name,
                discovery=settings.discovery,
                channel_settings=settings.channel,
                interceptors=self._interceptors,
            )
            for alias, service_name in settings.discovery.services.items()
        }
        self._reconcile_task: asyncio.Task[None] | None = None

    @property
    def settings(self) -> GrpcSettings:
        return self._settings

    async def start(self) -> None:
        for alias, channel in self._channels.items():
            await self._instance_source.subscribe_instances(
                channel.service_name,
                self._create_instance_callback(alias),
            )
            await self._refresh(alias)
        self._start_reconcile()

    async def reload(self, settings: GrpcSettings) -> None:
        previous_channels = dict(self._channels)
        next_aliases = set(settings.discovery.services)

        for alias in set(previous_channels) - next_aliases:
            channel = previous_channels.pop(alias)
            await channel.close()

        for alias, service_name in settings.discovery.services.items():
            channel = previous_channels.get(alias)
            if channel is None:
                continue
            if channel.service_name != service_name:
                previous_channels.pop(alias)
                await channel.close()
                continue
            await channel.update_settings(settings.discovery, settings.channel)
            await self._refresh(alias)

        self._settings = settings
        self._channels = previous_channels
        for alias, service_name in settings.discovery.services.items():
            if alias in self._channels:
                continue
            channel = ManagedChannel[T](
                alias=alias,
                service_name=service_name,
                discovery=settings.discovery,
                channel_settings=settings.channel,
                interceptors=self._interceptors,
            )
            self._channels[alias] = channel
            await self._instance_source.subscribe_instances(
                service_name,
                self._create_instance_callback(alias),
            )
            await self._refresh(alias)

    async def close(self) -> None:
        if self._reconcile_task is not None and not self._reconcile_task.done():
            self._reconcile_task.cancel()
        channels = list(self._channels.values())
        for channel in channels:
            await channel.close()
        if self._reconcile_task is not None:
            try:
                await self._reconcile_task
            except asyncio.CancelledError:
                pass

    def stub(self, alias: str, factory: Callable[..., T]) -> T:
        channel = self._channels.get(alias)
        if channel is None:
            raise ServiceUnavailableError(f"Unknown gRPC service alias: {alias}")
        return channel.stub(factory)

    def snapshot(self) -> dict[str, tuple[str, ...]]:
        return {
            alias: channel.addresses
            for alias, channel in self._channels.items()
        }

    def _create_instance_callback(self, alias: str) -> Any:
        async def on_instances_changed(instances: Sequence[Any]) -> None:
            channel = self._channels.get(alias)
            if channel is not None:
                channel.on_instances_change(instances)

        return on_instances_changed

    async def _refresh(self, alias: str) -> None:
        channel = self._channels.get(alias)
        if channel is None:
            return
        instances = await self._instance_source.list_healthy_instances(
            channel.service_name
        )
        await channel.refresh_instances(instances)

    def _start_reconcile(self) -> None:
        interval = self._settings.discovery.reconcile_interval_seconds
        if interval <= 0 or self._reconcile_task is not None:
            return
        self._reconcile_task = asyncio.create_task(self._reconcile(interval))

    async def _reconcile(self, interval_seconds: int) -> None:
        while True:
            await asyncio.sleep(interval_seconds)
            for alias in list(self._channels):
                try:
                    await self._refresh(alias)
                except Exception:
                    logger.exception("gRPC instance reconcile failed: alias=%s", alias)
