from collections.abc import Awaitable, Callable, Sequence
from typing import Any, Protocol


InstanceChangeCallback = Callable[[Sequence[Any]], Awaitable[None]]


class NacosInstanceSource(Protocol):
    async def subscribe_instances(
        self,
        service_name: str,
        callback: InstanceChangeCallback,
    ) -> None:
        ...

    async def list_healthy_instances(self, service_name: str) -> Sequence[Any]:
        ...


def healthy_instance_addresses(instances: Sequence[Any]) -> tuple[str, ...]:
    addresses = {
        f"{instance.ip}:{instance.port}"
        for instance in instances
        if instance.enabled and instance.healthy and instance.weight > 0
    }
    return tuple(sorted(addresses))
