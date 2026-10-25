from types import SimpleNamespace

from xxs_nacos_grpc import healthy_instance_addresses


def instance(ip: str, enabled: bool = True, healthy: bool = True, weight: float = 1) -> SimpleNamespace:
    return SimpleNamespace(ip=ip, port=9000, enabled=enabled, healthy=healthy, weight=weight)


def test_healthy_instance_addresses_filters_and_sorts() -> None:
    addresses = healthy_instance_addresses(
        [
            instance("10.0.0.2"),
            instance("10.0.0.1"),
            instance("10.0.0.3", enabled=False),
            instance("10.0.0.4", healthy=False),
            instance("10.0.0.5", weight=0),
        ]
    )

    assert addresses == ("10.0.0.1:9000", "10.0.0.2:9000")
