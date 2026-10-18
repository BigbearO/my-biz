# 技术方案-云端-对话服务-Nacos注册-已实现

> 来源：[《客户端监听.md》](../客户端监听.md) §3.6（内容重组收录，原文件保留未动）
> 关联：[技术方案-云端-gRPC发现库xxs-nacos-grpc-已实现](技术方案-云端-gRPC发现库xxs-nacos-grpc-已实现.md)、[技术方案-云端-对话服务-框架与生命周期](技术方案-云端-对话服务-框架与生命周期.md)

## 一、功能与职责（总）

### 1.1 功能定位

本服务作为 Nacos 注册中心的服务提供方：启动时把 HTTP 实例（`goodfish-ai-service`）注册为**临时实例（ephemeral）**，供网关 `lb://goodfish-ai-service` 负载均衡转发；优雅关闭时先注销实例摘除流量，再停进程。

### 1.2 职责边界 / 范围

- **注册范围**：本服务对外**只提供 HTTP**；gRPC 仅作**客户端**，**本期不注册 gRPC 实例**。
- 临时实例随 Nacos gRPC 长连接存活，**应用层无需自行心跳**（连接断开服务端自动摘除）。
- 服务发现（下游 `{app}-grpc` 的订阅、channel 装配、负载均衡）由可复用库 `xxs-nacos-grpc` 承担，本文只描述本服务的注册/注销与统一封装。

### 1.3 与其他模块的关系

| 模块 | 关系 |
|---|---|
| 网关 | 消费 `lb://goodfish-ai-service`，只路由到健康实例 |
| 生命周期 | 注册发生在 lifespan startup 末尾，注销是 lifespan shutdown 首步（见 [技术方案-云端-对话服务-框架与生命周期](技术方案-云端-对话服务-框架与生命周期.md)） |
| 下游基础服务 | 商品服务 / 模型服务按契约注册 `-grpc` 服务名，供本服务发现消费（见 [技术方案-云端-gRPC发现库xxs-nacos-grpc-已实现](技术方案-云端-gRPC发现库xxs-nacos-grpc-已实现.md)） |
| bootstrap | `NacosManager` 的连接属性、注册 IP、注册端口全部取自 bootstrap（见 [技术方案-云端-对话服务-多环境与配置中心](技术方案-云端-对话服务-多环境与配置中心.md)） |

## 二、逻辑实现（分）

### 2.1 总体设计

- Nacos SDK（`nacos-sdk-python==3.2.0`，v2 异步 API）与服务端之间是 **gRPC 双向流长连接**，SDK 自动维持连接级保活。
- 临时实例（ephemeral）的生命周期 = 连接的生命周期：进程正常退出（deregister）、崩溃、断网，服务端都会及时摘除实例。
- 结果：网关 `lb://goodfish-ai-service` 永远只路由到健康实例。
- `NacosManager` 的生命周期由 FastAPI lifespan 管理（start / shutdown），不做模块级单例。

### 2.2 服务注册参数

| 参数 | 值 | 说明 |
|---|---|---|
| service_name | `goodfish-ai-service` | 常量，见 `app/constants.py` |
| group_name | `DEFAULT_GROUP` | 取 bootstrap |
| cluster_name | `DEFAULT` | 单机房默认 |
| ip / port | 本机 IP（或 `NACOS_REGISTER_IP`）+ **`SERVER_PORT`（bootstrap）** | 网关 lb 转发的目标地址；**禁止 `0.0.0.0`** |
| weight | 1.0 | 单实例部署，权重无意义，留默认 |
| **ephemeral** | **True** | 临时实例：gRPC 连接断开/进程退出 → 服务端自动摘除，**无需应用层心跳** |
| enabled / healthy | True / True | 上线即可接流量（注册发生在 lifespan startup 末尾，此时依赖已全部就绪） |
| metadata | `{"protocol":"http","secure":"false","cluster":"DEFAULT","env":...,"version":...}` | 与 Java 侧键名一致 |
| **workers** | **必须 = 1** | 多 worker 共用端口会导致注册/注销互相踩踏（详见 [技术方案-云端-对话服务-框架与生命周期](技术方案-云端-对话服务-框架与生命周期.md) §2.3） |

**注册范围**：

| 服务名 | port | protocol | 消费方 |
|---|---|---|---|
| `goodfish-ai-service` | `SERVER_PORT`（默认 8000） | `http` | 网关 `lb://goodfish-ai-service` |
| `goodfish-ai-service-grpc` | `GRPC_PORT`（默认 9000） | `grpc` | **本期不注册**；仅当 Java 侧要调 Python 时才启用 |

消费规则：

- **网关**消费 `lb://goodfish-ai-service`
- **内部 gRPC 客户端**订阅下游的 `{app}-grpc`，**只按服务名 + group + namespace 查询**，并在客户端做 `enabled and healthy` 过滤
- **后缀 `-grpc` 仅用于注册时推断 `metadata.protocol`**，发现侧不解析后缀

> ⚠️ **不要用"同一服务名 + `metadata.protocol` 过滤"的旧写法**：gRPC 客户端无法在 target 里表达 metadata 条件，会退化成"查到 HTTP 实例却发现端口不通"，且各语言的过滤实现不一致。改为**两个独立服务名**（`{app}` / `{app}-grpc`），与 Java 侧 `xxs-quarkus-nacos-discovery` 的 `-grpc` 后缀约定一致。
>
> 完整契约、可复用封装与实现代码见 [技术方案-云端-gRPC发现库xxs-nacos-grpc-已实现](技术方案-云端-gRPC发现库xxs-nacos-grpc-已实现.md)。

### 2.3 注册 / 注销流程

**注册流程**（lifespan startup 末尾执行，此时 DB / Redis / gRPC 通道均已就绪）：

1. `resolve_register_ip()` 解析注册 IP：优先显式配置（容器/K8s 必填），否则探测本机出口 IP（绝不使用 `0.0.0.0`）
2. `register_instance(service_name=SERVICE_NAME_AI, port=bootstrap.server_port, metadata={"env":..., "version":...})`
3. `protocol` 由服务名后缀推导（`-grpc` → grpc，否则 http），只写入实例 metadata 供运维辨识
4. 注册成功后实例参数入栈（`_registered_params`），供注销时逐条还原

**注销流程**（lifespan shutdown 首步，先摘流量再停进程）：

1. `deregister_all()`：校验 `_registered_params`（未注册成功时静默跳过），按注册的**逆序**逐个注销
2. 单个注销失败只记日志、不阻断优雅关闭
3. 注销完成后才依次关闭下游 gRPC 通道、Redis、DB、Nacos SDK 连接

### 2.4 健康检查与保活机制

- v2 SDK 与服务端之间是 **gRPC 双向流长连接**，SDK 自动维持连接级保活，应用层不做 5s 心跳
- 临时实例的生命周期 = 连接的生命周期：进程正常退出（deregister）、崩溃、断网，服务端都会及时摘除实例
- 结果：网关 `lb://goodfish-ai-service` 永远只路由到健康实例
- 北向另有 `/health` 探针（免鉴权）；gRPC 侧预留 `grpcio-health-checking` 供运维探活

### 2.5 服务发现（下游基础服务）

**前提**：下游基础服务需按注册契约注册：

| 下游服务 | 需注册的 gRPC 服务名 | 说明 |
|---|---|---|
| 商品服务（Java） | `goodfish-item-service-grpc` | `goodfish-item-service` 是其 HTTP 名，本服务消费 `-grpc` |
| 模型服务（Go/Kratos，`backend/llm-manage`） | `goodfish-llm-service-grpc` | 同上 |

> ✅ **模型服务已集成 Nacos 服务注册**（无开发项）；商品服务按 RuoYi 标准注册。二者都需按契约注册 `-grpc` 服务名。
>
> ⚠️ **注册到 `-grpc` 服务名下的实例端口必须是 gRPC 端口**。若把 REST 端口注册上去，服务发现会"成功"但 gRPC 调用必然失败（连接被拒或协议不匹配），现象极易误导排查方向。

发现策略：**subscribe 推平 + 本地缓存（客户端做 `enabled and healthy` 过滤） + gRPC 内置 `round_robin`**。

> 实现已抽取为可复用库 `xxs-nacos-grpc`，设计与代码见 [技术方案-云端-gRPC发现库xxs-nacos-grpc-已实现](技术方案-云端-gRPC发现库xxs-nacos-grpc-已实现.md)；手写订阅缓存 + 按 `metadata.protocol` 过滤 + 手写轮询的实现方式已弃用（gRPC 客户端无法在 target 中表达 metadata 条件，且轮询/故障摘除由 gRPC 内置策略完成更可靠）。
>
> **gRPC TLS**：已确定不使用（内网明文）。仅当出现跨 VPC / 跨机房调用时，再评估启用 mTLS 并同步更新 Nacos metadata。

### 2.6 Nacos 客户端统一封装

以下为本服务内的落地实现（可复用版本已抽取为独立库 `xxs-nacos-grpc`，新服务应直接引用该库，不再复制粘贴）。与库的主要差异：① `register_instance()` 去掉 `protocol` / `role` 参数，`protocol` 由服务名后缀推导；② 新增 gRPC channel 管理（`ManagedChannel`，替代手写解析器）；③ 通过**业务钩子**解耦 DB/Redis，库本身不依赖业务。

```python
# app/nacos/manager.py
"""Nacos 3.2 接入统一封装。

- 官方 v2 异步 SDK（nacos-sdk-python==3.2.0；3.0.0~3.0.2 已被 PyPI yanked）
- 生命周期由 FastAPI lifespan 管理（start / shutdown），不做模块级单例
- 只导入 v2.nacos 真实导出的符号（NamingService 不存在，导入即 ImportError）
"""
import asyncio
import logging
import socket
from typing import Awaitable, Callable

from v2.nacos import (
    ClientConfigBuilder,
    ConfigParam,
    DeregisterInstanceParam,
    GRPCConfig,
    NacosConfigService,
    NacosNamingService,
    RegisterInstanceParam,
    SubscribeServiceParam,
)

from app.bootstrap import BootstrapSettings
from app.constants import CLUSTER_NAME_DEFAULT, GRPC_SUFFIX

logger = logging.getLogger(__name__)

GRPC_TIMEOUT_MS = 5000
CONNECT_RETRY_ATTEMPTS = 3          # Nacos 滚动重启时的容错窗口
CONNECT_RETRY_DELAY_SECONDS = 2

ConfigListener = Callable[[str, str, str, str], Awaitable[None]]


class ConfigNotFoundError(Exception):
    """远程配置不存在（namespace / Data ID / Group 任一不匹配）。"""


class NacosManager:
    def __init__(self, bootstrap: BootstrapSettings, service_ip: str) -> None:
        self._bootstrap = bootstrap
        self._ip = service_ip
        self._config_client: NacosConfigService | None = None
        self._naming_client: NacosNamingService | None = None
        # 记录每个「已成功注册」的实例参数，注销时逐个还原
        self._registered_params: list[RegisterInstanceParam] = []

    # ---------- 生命周期 ----------

    async def start(self) -> None:
        builder = (
            ClientConfigBuilder()
            .server_address(self._bootstrap.nacos_server_addr)
            .namespace_id(self._bootstrap.nacos_namespace_id)   # 环境隔离的关键
            .username(self._bootstrap.nacos_username)
            .password(self._bootstrap.nacos_password)
            .log_level(self._bootstrap.nacos_log_level)
            .cache_dir(self._bootstrap.nacos_cache_dir)         # 快照目录（降级来源）
            .log_dir(self._bootstrap.nacos_log_dir)
            .grpc_config(GRPCConfig(grpc_timeout=GRPC_TIMEOUT_MS))
        )
        if self._bootstrap.nacos_context_path:                  # 仅 Nacos 自定义 context-path 时设置
            builder.context_path(self._bootstrap.nacos_context_path)
        client_config = builder.build()

        last_error: Exception | None = None
        for attempt in range(1, CONNECT_RETRY_ATTEMPTS + 1):
            try:
                self._config_client = await NacosConfigService.create_config_service(client_config)
                self._naming_client = await NacosNamingService.create_naming_service(client_config)
                return
            except Exception as exc:            # 连接类异常统一重试
                last_error = exc
                logger.warning("Nacos 连接失败（第 %s/%s 次）: %s",
                               attempt, CONNECT_RETRY_ATTEMPTS, exc)
                await asyncio.sleep(CONNECT_RETRY_DELAY_SECONDS)
        # 配置中心是强依赖，重试耗尽即快速失败，避免"半启动"服务注册上去放大故障
        raise RuntimeError("Nacos 连接失败，服务拒绝启动") from last_error

    async def shutdown(self) -> None:
        if self._naming_client is not None:
            await self._naming_client.shutdown()
        if self._config_client is not None:
            await self._config_client.shutdown()

    # ---------- 配置中心 ----------

    async def get_app_config(self) -> str:
        content = await self._config_client.get_config(ConfigParam(
            data_id=self._bootstrap.nacos_data_id,
            group=self._bootstrap.nacos_group,
        ))
        if not content:
            raise ConfigNotFoundError(
                f"data_id={self._bootstrap.nacos_data_id}, "
                f"group={self._bootstrap.nacos_group}, "
                f"namespace={self._bootstrap.nacos_namespace_id}"
            )
        return content

    async def watch_app_config(self, listener: ConfigListener) -> None:
        """注册配置监听。

        ★ 回调真实签名是 (tenant, group, data_id, content) —— 以 SDK 源码
        `SubscribeCacheData.execute_listener()` 为准；官方 README 示例写成
        (tenant, data_id, group, content) 是错的，照抄会串参。
        """

        async def _safe_listener(*args: str) -> None:
            try:
                await listener(*args)
            except Exception:                   # 回调内异常不能影响 SDK 监听线程
                logger.exception("配置变更处理失败，保留旧配置")

        await self._config_client.add_listener(
            self._bootstrap.nacos_data_id,
            self._bootstrap.nacos_group,
            _safe_listener,
        )

    # ---------- 注册中心 ----------

    async def register_instance(
        self,
        service_name: str,
        port: int,
        metadata: dict[str, str],
    ) -> None:
        """注册单个实例。

        `protocol` 由服务名后缀推导（`-grpc` → grpc，否则 http），
        与 Java 侧 `NacosConvert.resolveProtocol` 语义一致；
        发现侧不解析后缀，只按服务名查询。
        """
        merged = {
            "protocol": resolve_protocol(service_name),
            "secure": "false",
            "cluster": CLUSTER_NAME_DEFAULT,
            **metadata,
        }
        param = RegisterInstanceParam(
            service_name=service_name,
            group_name=self._bootstrap.nacos_group,
            cluster_name=CLUSTER_NAME_DEFAULT,
            ip=self._ip,
            port=port,
            weight=1.0,
            ephemeral=True,      # 临时实例：随 Nacos gRPC 连接存活，进程退出自动摘除
            enabled=True,
            metadata=merged,
        )
        await self._naming_client.register_instance(request=param)
        self._registered_params.append(param)       # 成功后入栈，供注销逐条还原
        logger.info(
            "已注册到 Nacos: namespace=%s service=%s %s:%s protocol=%s",
            self._bootstrap.nacos_namespace_id, service_name, self._ip, port, merged["protocol"],
        )

    async def deregister_all(self) -> None:
        """注销全部已注册实例（HTTP + gRPC）；未注册成功时静默跳过。"""
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
            except Exception:                       # 单个注销失败不能阻断优雅关闭
                logger.exception("注销实例失败: %s:%s", param.ip, param.port)
        self._registered_params.clear()

    async def subscribe_service(
        self,
        service_name: str,
        callback: Callable[[list], Awaitable[None]],
    ) -> None:
        """订阅服务实例变更。callback 签名：async def callback(instance_list: list[Instance])"""
        await self._naming_client.subscribe(SubscribeServiceParam(
            service_name=service_name,
            group_name=self._bootstrap.nacos_group,
            subscribe_callback=callback,
        ))


def resolve_protocol(service_name: str, suffix: str = GRPC_SUFFIX) -> str:
    """按服务名后缀推断协议（`-grpc` → grpc，否则 http）。

    与 Java 侧 `NacosConvert.resolveProtocol` 语义一致；
    该值只写入实例 metadata 供运维辨识，**发现侧不据此过滤**。
    """
    return "grpc" if service_name.endswith(suffix) else "http"


def resolve_register_ip(override: str) -> str:
    """注册 IP：优先显式配置（容器/K8s 必填），否则探测本机出口 IP。

    注册 IP 绝不能是 0.0.0.0，否则网关无法转发。
    """
    if override:
        return override
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.settimeout(1.0)
            sock.connect(("8.8.8.8", 80))       # 不实际发包，仅借路由表取出口 IP
            return sock.getsockname()[0]
    except OSError:
        # 无外网/无默认路由的隔离环境：退化为主机名解析
        return socket.gethostbyname(socket.gethostname())
```

> 配置监听回调的真实签名是 `(tenant, group, data_id, content)`——以 SDK 源码 `SubscribeCacheData.execute_listener()` 为准；官方 README 示例写成 `(tenant, data_id, group, content)` 是错的，照抄会串参。

## 五、异常与容错

| 异常场景 | 处理方案 |
|---|---|
| **Nacos 连接失败（启动期）** | 重试 3 次（间隔 2s，覆盖 Nacos 滚动重启窗口）；仍失败抛 `RuntimeError("Nacos 连接失败，服务拒绝启动")` 快速失败——配置中心是强依赖，避免"半启动"服务注册上去放大故障 |
| **远程配置不存在** | 抛 `ConfigNotFoundError`（带 namespace / Data ID / Group 定位信息），启动失败 |
| **注销单个实例失败** | 记异常日志，继续注销其余实例，不阻断优雅关闭 |
| **进程崩溃 / SIGKILL** | ephemeral 实例随 gRPC 连接断开被 Nacos 自动摘除，网关不再转发 |
| **注册 IP 探测失败** | 无外网/无默认路由的隔离环境退化为按主机名解析 |
| **本地快照降级** | `NACOS_CACHE_DIR` 快照目录为 Nacos 不可用时的降级来源 |

## 七、TODO（待优化）

| 项目 | 说明 |
|---|---|
| **内网 gRPC 端口隔离** | 内部调用已改 gRPC（不过网关），需在部署层固化：gRPC 端口只对集群内网开放（K8s NetworkPolicy / 安全组），严禁暴露公网 |
