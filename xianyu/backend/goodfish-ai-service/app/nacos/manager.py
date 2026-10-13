import asyncio
import ipaddress
import logging
import socket
from typing import Any, Awaitable, Callable, Protocol

from v2.nacos import (
    ClientConfigBuilder,
    ConfigParam,
    DeregisterInstanceParam,
    GRPCConfig,
    NacosConfigService,
    NacosNamingService,
    RegisterInstanceParam,
)

from ..bootstrap import BootstrapSettings
from ..constants import (
    GRPC_TIMEOUT_MS,
    DEFAULT_INSTANCE_WEIGHT,
    NACOS_REGISTER_IP_PROBE_HOST,
    NACOS_REGISTER_IP_PROBE_PORT,
    NACOS_REGISTER_IP_PROBE_TIMEOUT_SECONDS,
    NACOS_CONNECT_RETRY_ATTEMPTS,
    NACOS_CONNECT_RETRY_DELAY_SECONDS,
    PROTOCOL_HTTP,
    REGISTER_IP_ANY,
    SECURE_FALSE,
    SERVICE_VERSION,
)
from ..errors import ConfigNotFoundError, NacosConnectionError, RegisterIpError

logger = logging.getLogger(__name__)

ConfigListener = Callable[[str, str, str, str], Awaitable[None]]


class ClientFactory(Protocol):
    async def __call__(self, client_config: Any, include_config: bool) -> tuple[Any, Any | None]:
        ...


async def create_nacos_clients(
    client_config: Any,
    include_config: bool,
) -> tuple[Any, Any | None]:
    config_client = None
    naming_client = None
    try:
        naming_client = await NacosNamingService.create_naming_service(client_config)
        if include_config:
            config_client = await NacosConfigService.create_config_service(client_config)
        return naming_client, config_client
    except Exception:
        await _shutdown_clients(naming_client, config_client)
        raise


class NacosManager:
    def __init__(
        self,
        bootstrap: BootstrapSettings,
        service_ip: str,
        client_factory: ClientFactory | None = None,
    ) -> None:
        self._bootstrap = bootstrap
        self._service_ip = service_ip
        self._client_factory: ClientFactory = client_factory or create_nacos_clients
        self._naming_client: Any | None = None
        self._config_client: Any | None = None
        self._registered_params: list[RegisterInstanceParam] = []

    @property
    def config_enabled(self) -> bool:
        return bool(self._bootstrap.nacos_data_id)

    async def start(self) -> None:
        client_config = self._build_client_config()
        last_error: Exception | None = None
        for attempt in range(1, NACOS_CONNECT_RETRY_ATTEMPTS + 1):
            try:
                naming_client, config_client = await self._client_factory(
                    client_config,
                    self.config_enabled,
                )
                self._naming_client = naming_client
                self._config_client = config_client
                return
            except Exception as exc:
                last_error = exc
                logger.warning(
                    "Nacos connection failed (attempt %s/%s): %s",
                    attempt,
                    NACOS_CONNECT_RETRY_ATTEMPTS,
                    exc,
                )
                if attempt < NACOS_CONNECT_RETRY_ATTEMPTS:
                    await asyncio.sleep(NACOS_CONNECT_RETRY_DELAY_SECONDS)
        raise NacosConnectionError("Nacos connection failed, service startup rejected") from last_error

    async def get_config(self) -> str:
        if self._config_client is None:
            raise RuntimeError("Nacos config client is not enabled")
        content = await self._config_client.get_config(
            ConfigParam(
                data_id=self._bootstrap.nacos_data_id,
                group=self._bootstrap.nacos_group,
            )
        )
        if content is None or not str(content).strip():
            raise ConfigNotFoundError(
                "Nacos config not found: "
                f"data_id={self._bootstrap.nacos_data_id}, "
                f"group={self._bootstrap.nacos_group}, "
                f"namespace={self._bootstrap.nacos_namespace_id}"
            )
        return content

    async def watch_config(self, listener: ConfigListener) -> None:
        if self._config_client is None:
            raise RuntimeError("Nacos config client is not enabled")

        async def safe_listener(tenant: str, group: str, data_id: str, content: str) -> None:
            try:
                await listener(tenant, group, data_id, content)
            except Exception:
                logger.exception("Nacos config update was rejected, previous config remains active")

        await self._config_client.add_listener(
            self._bootstrap.nacos_data_id,
            self._bootstrap.nacos_group,
            safe_listener,
        )

    async def register_http_instance(self) -> None:
        if self._naming_client is None:
            raise RuntimeError("Nacos naming client is not started")
        metadata = {
            "protocol": PROTOCOL_HTTP,
            "secure": SECURE_FALSE,
            "cluster": self._bootstrap.nacos_cluster_name,
            "env": self._bootstrap.app_env,
            "version": SERVICE_VERSION,
        }
        param = RegisterInstanceParam(
            service_name=self._bootstrap.nacos_service_name,
            group_name=self._bootstrap.nacos_group,
            cluster_name=self._bootstrap.nacos_cluster_name,
            ip=self._service_ip,
            port=self._bootstrap.server_port,
            weight=DEFAULT_INSTANCE_WEIGHT,
            ephemeral=True,
            enabled=True,
            metadata=metadata,
        )
        await self._naming_client.register_instance(request=param)
        self._registered_params.append(param)
        logger.info(
            "Registered HTTP instance to Nacos: namespace=%s service=%s address=%s:%s",
            self._bootstrap.nacos_namespace_id,
            self._bootstrap.nacos_service_name,
            self._service_ip,
            self._bootstrap.server_port,
        )

    async def deregister_all(self) -> None:
        if self._naming_client is None:
            return
        for param in reversed(self._registered_params):
            try:
                await self._naming_client.deregister_instance(
                    request=DeregisterInstanceParam(
                        service_name=param.service_name,
                        group_name=param.group_name,
                        cluster_name=param.cluster_name,
                        ip=param.ip,
                        port=param.port,
                        ephemeral=True,
                    )
                )
            except Exception:
                logger.exception(
                    "Failed to deregister Nacos instance: service=%s address=%s:%s",
                    param.service_name,
                    param.ip,
                    param.port,
                )
        self._registered_params.clear()

    async def shutdown(self) -> None:
        await _shutdown_clients(self._naming_client, self._config_client)
        self._naming_client = None
        self._config_client = None

    def _build_client_config(self) -> Any:
        builder = (
            ClientConfigBuilder()
            .server_address(self._bootstrap.nacos_server_addr)
            .namespace_id(self._bootstrap.nacos_namespace_id)
            .username(self._bootstrap.nacos_username or "")
            .password(self._bootstrap.nacos_password or "")
            .log_level(self._bootstrap.nacos_log_level)
            .cache_dir(self._bootstrap.nacos_cache_dir)
            .log_dir(self._bootstrap.nacos_log_dir)
            .grpc_config(GRPCConfig(grpc_timeout=GRPC_TIMEOUT_MS))
        )
        if self._bootstrap.nacos_context_path:
            builder = builder.context_path(self._bootstrap.nacos_context_path)
        return builder.build()


async def _shutdown_clients(naming_client: Any | None, config_client: Any | None) -> None:
    if naming_client is not None:
        try:
            await naming_client.shutdown()
        except Exception:
            logger.exception("Failed to shutdown Nacos naming client")
    if config_client is not None:
        try:
            await config_client.shutdown()
        except Exception:
            logger.exception("Failed to shutdown Nacos config client")


def resolve_register_ip(override: str) -> str:
    explicit_ip = override.strip()
    if explicit_ip:
        return _validate_register_ip(explicit_ip)
    return _validate_register_ip(_detect_local_outbound_ip())


def _detect_local_outbound_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.settimeout(NACOS_REGISTER_IP_PROBE_TIMEOUT_SECONDS)
            sock.connect((NACOS_REGISTER_IP_PROBE_HOST, NACOS_REGISTER_IP_PROBE_PORT))
            return str(sock.getsockname()[0])
    except OSError:
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError as exc:
            raise RegisterIpError("Cannot resolve service registration IP") from exc


def _validate_register_ip(candidate: str) -> str:
    try:
        parsed_ip = ipaddress.ip_address(candidate)
    except ValueError as exc:
        raise RegisterIpError(f"Invalid service registration IP: {candidate}") from exc
    if parsed_ip.version != 4 or parsed_ip == ipaddress.ip_address(REGISTER_IP_ANY):
        raise RegisterIpError(f"Service registration IP is not usable: {candidate}")
    return candidate
