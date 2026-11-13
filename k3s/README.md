# k3s 单节点学习环境方案

在 Debian 12 单节点（8C / 32G）上，用 k3s 搭建一套贴近企业实践的云原生体系：GitOps 发布流水线 + 微服务业务栈 + 统一可观测。当前只部署 **biz-test 一个环境**，结构上预留多环境（biz-dev / biz-test）扩展能力。

## 1. 总体架构

```
                          ngrok 隧道（公网域名）
                                 │
  开发者 ──内网 nip.io──┐        │
                        ▼        ▼
                   ┌──────────────────────┐
                   │  Traefik (k3s 自带)   │  按域名路由
                   └─────────┬────────────┘
                             │ Ingress
   ┌─────────────────────────┼──────────────────────────────┐
   │                        k3s 单节点 (8C/32G)               │
   │  ┌───────────┐   ┌─────────────┐   ┌────────────────┐   │
   │  │  cicd     │   │ middleware  │   │     biz-test   │   │
   │  │ Argo CD   │   │ Nacos(pgsql)│   │ 11 个微服务     │   │
   │  └───────────┘   │ PostgreSQL  │   │ (网关是唯一      │   │
   │  ┌───────────┐   │ Redis       │   │  Ingress 暴露点)│   │
   │  │  infra    │   └─────────────┘   └────────────────┘   │
   │  │ otel-collector → OpenObserve (logs/metrics/traces)    │
   │  └───────────┘                                            │
   └───────────────────────────────────────────────────────────┘
```

## 2. 环境模型

| | biz-test（当前） | biz-dev（预留，未启用） |
|---|---|---|
| 业务 namespace | `biz-test` | `biz-dev` |
| 中间件 namespace | `middleware-test` | `middleware-dev` |
| 中间件 | PG + Redis + Nacos(pgsql) 各一套 | 各一套（启用时才创建） |
| 发布方式 | 晋升 + 手动双确认 | `[deploy]` 标记触发自动发布 |
| 外部访问 | ngrok 域名 + 内网 nip.io | 预留独立域名 |

多环境的关键开关：**gitops 仓库中是否存在对应的 Argo CD Application 清单**。启用 dev = 提交 dev 的 Application 清单；禁用 = 删除该清单。`overlays/dev/` 目前只放占位骨架，不消耗任何集群资源。

## 3. 组件与资源预算

| 层 | 组件 | 形态 | limit |
|---|---|---|---|
| 底座 | k3s + Traefik + local-path | 系统自带 | ~2G（系统层） |
| 底座 | metrics-server | Deployment | 256M |
| cicd | Argo CD | Helm 安装 | ~1G |
| infra | otel-collector | Deployment ×1 | 256M |
| infra | OpenObserve | StatefulSet + PVC | 1.5G |
| middleware-test | PostgreSQL | Deployment + PVC | 512M |
| middleware-test | Redis | Deployment + PVC | 128M |
| middleware-test | Nacos（单机模式，pgsql 存储） | Deployment + PVC | 1G |
| biz-test | 11 个微服务 | Deployment ×11，副本数 1 | ~7G（Java 768M/个，Go/Python 128~256M/个） |
| 合计 | | | **~14G / 32G**，余量充足 |

所有 Pod 强制写 requests/limits；每个 namespace 配 ResourceQuota + LimitRange；JVM 统一 `-XX:MaxRAMPercentage=50` 适配容器内存。

## 4. 仓库布局（配置与业务分离）

**业务仓库**（11 个微服务，各自原 repo）：只新增两样东西——
- `Dockerfile`
- `.github/workflows/build.yml`（见 §6）

**gitops 配置仓库**（独立新建，私有）：

```
gitops/
├── apps/                              # 根 Application 挂载点
│   ├── argocd/                        # Argo CD 自身（Helm values）
│   ├── middleware/
│   │   ├── base/                      # 中间件公共 values
│   │   ├── overlays/
│   │   │   ├── test/                  # test 环境差异（当前生效）
│   │   │   └── dev/                   # 占位骨架，暂不建 Application
│   │   ├── postgres-application.yaml
│   │   ├── redis-application.yaml
│   │   └── nacos-application.yaml
│   ├── infra/
│   │   ├── otel-collector-application.yaml
│   │   └── openobserve-application.yaml
│   └── biz/
│       ├── base/                      # 微服务通用清单模板（Kustomize base）
│       ├── overlays/
│       │   ├── test/                  # 每服务一个目录：镜像 tag、env、资源
│       │   └── dev/                   # 占位骨架
│       └── applications/              # 11 个服务的 Application 清单
├── root-app.yaml                      # app-of-apps 根 Application
└── secrets/                           # 明文 Secret（私有仓库，见 §8）
```

工具选型：**中间件用社区 Helm Chart**（bitnami postgresql/redis、Nacos 官方 chart 或手写清单，改 values 接 pgsql）；**自研微服务用 Kustomize**（base 定模板，overlay 只写差异：镜像、端口、环境变量、资源规格）。

Argo CD 用 **app-of-apps**：一个根 Application 监听 `apps/` 目录，自动管理全部子 Application。

## 5. 底座安装顺序（bootstrap）

按依赖顺序执行，每步验证后再进下一步：

1. **k3s 安装**：官方国内脚本，禁用不需要的组件可保持默认（Traefik 保留）
2. **镜像加速**：`/etc/rancher/k3s/registries.yaml` 配 docker.io 国内 mirror + GHCR 镜像源，重启 k3s 生效
3. **metrics-server**：`kubectl apply` 官方清单，验证 `kubectl top nodes`
4. **Argo CD**：Helm 安装到 `cicd` namespace，配 Ingress（nip.io 域名）
5. **gitops 仓库**：按 §4 结构初始化，先提交 root-app.yaml，Argo CD 接管
6. **middleware-test**：三件套 Application 逐一启用，验证连通性（Nacos 控制台、PG 连接、Redis ping）
7. **infra**：otel-collector → OpenObserve，验证 OTLP 上报通路
8. **biz-test**：11 个服务 Application 逐个（或分批）启用，从网关开始

本地工具：kubectl（kubeconfig 从服务器拷出）、helm、k9s。

## 6. CI 流程（GitHub Actions）

触发条件：**commit message 包含 `[deploy]` 标记**，否则不构建不部署。

```
push (含 [deploy]) 
  → Actions 构建镜像，tag = git short sha
  → 推送 GHCR
  → 回写 gitops 仓库：更新 biz/overlays/test/<服务>/ 中的镜像 tag
  → Argo CD 检测到 gitops 变更（biz-test Application 为手动同步）
```

当前单环境下，标记的语义是"哪些 commit 产生镜像并更新部署清单"；上线仍需手动 SYNC。将来启用 dev 环境后，同一机制自动升级为 dev 的"标记即发布"。

## 7. 发布流程（晋升 + 双确认）

生产（test）发版**不重新构建**，只晋升已验证镜像：

1. 开发者在 GitHub Actions 页面手动触发 `workflow_dispatch`（promotion workflow），输入要发布的镜像 tag（git short sha）
2. workflow 校验该 tag 存在于 GHCR，回写 gitops 仓库 test overlay
3. 打开 Argo CD，找到 biz-test 对应 Application（此时显示 OutOfSync），**手动点 SYNC**
4. 上线完成

三重留痕：workflow run 记录 + Argo CD sync 记录 + gitops 仓库 commit。

**回滚**：revert gitops 仓库的那个 commit（或直接在 Argo CD 里选上一个 sync 的历史版本重新 sync），集群回到旧版本。练习场景：
- 手动 `kubectl delete deployment` → 观察 selfHeal 救回（仅对自动同步的应用有意义，test 为手动同步时演示用 dev 或临时开启）
- rollback 镜像 tag → 走一次完整回滚
- 修改集群内资源（`kubectl edit`）→ 观察 Argo CD 标记 drift

## 8. 密钥与其他

- **密钥**：明文 Secret 直接提交私有 gitops 仓库（GHCR pull secret 配到 ServiceAccount 全局生效；PG/Redis/Nacos 密码；OpenObserve token）。体系跑通后第一个进阶实验：迁移到 Sealed Secrets。
- **服务发现**：11 个服务全部注册 Nacos（已验证），每环境网关服务是唯一 Ingress 暴露点。
- **可观测**：业务 Pod 注入 OTLP 环境变量（`otel-collector.infra:4317`，OTLP gRPC）；OpenObserve 保留策略 logs 7d / traces 3d / metrics 15d；trace 先 100% 全采。不使用 Grafana。
- **Ingress**：按域名路由（host 字段集中配置），当前 ngrok 域名 → test 网关、内网 nip.io → 各面板；将来加环境或换真域名只改 host 配置，链路不变。
- **HPA**：挑 1 个 Java 服务配 CPU 扩缩容（min 1 / max 3）。

## 9. 进阶实验清单（主线跑通后）

1. Sealed Secrets 密钥迁移
2. Longhorn 分布式存储替换 local-path
3. CloudNativePG Operator 管理 PG
4. Grafana 对接 OpenObserve 数据源
5. tail-sampling 降 trace 采样率
6. 多环境启用：提交 dev Application 清单 + `[deploy]` 自动发布

## 10. ADR 索引

- [0001 - GitOps 配置仓库与业务仓库分离](docs/adr/0001-gitops-repo-separation.md)
- [0002 - 全服务注册 Nacos](docs/adr/0002-all-services-register-nacos.md)
- [0003 - 发布门禁策略](docs/adr/0003-release-gating.md)
- [0004 - k3s 单节点与 local-path 存储](docs/adr/0004-k3s-local-path-single-node.md)
