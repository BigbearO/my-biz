# Spec:k3s 单节点学习环境 —— GitOps + 微服务 + 统一可观测完整体系

Status: ready-for-agent
来源:k3s/README.md 方案文档 + GLOSSARY.md + ADR 0001~0004(to-spec 产出)

## Problem Statement

我想在 Debian 12 单节点(8C/32G)上,用 k3s 搭一套贴近企业实践的云原生环境来学习研究。零散地 `kubectl apply` 几个 YAML 学不到完整体系:发布没有门禁和留痕,集群状态没有唯一事实来源,微服务没有统一的服务发现和可观测,资源没有治理约束。我需要的是一条完整的、可审计的、和真实企业形态同构的链路——代码到镜像、镜像到集群、集群到可观测——而不是一个能跑起来的玩具。

## Solution

按依赖顺序 bootstrap 一套 k3s 单节点环境(学习/研究用途,部署 biz-test 一个环境,结构上预留多环境扩展):

- **GitOps 为唯一事实来源**:Argo CD(app-of-apps)只监听一个独立的私有 gitops 配置仓库;任何人改集群状态都必须经该仓库,直接 kubectl 修改会被漂移检测暴露。
- **CI 门禁**:业务仓库(11 个微服务,各自原 repo)只加 Dockerfile 和构建 workflow;commit message 含 `[deploy]` 才构建镜像(tag = git short sha)推 GHCR 并回写 gitops 仓库。
- **发布晋升 + 双确认**:生产(test)发版不重新构建,workflow_dispatch 手动晋升已验证镜像,再在 Argo CD 手动 SYNC 才生效;三重留痕(workflow run + Argo CD sync 记录 + gitops commit),回滚 = revert commit。
- **中间件层**:middleware-test 一套 PostgreSQL + Redis + Nacos(单机模式,pgsql 存储),11 个跨语言微服务(Spring Cloud Alibaba / go-kratos / Python)统一注册 Nacos。
- **统一可观测**:otel-collector 接收业务 Pod 的 OTLP(gRPC),转发 OpenObserve 集中存 logs/metrics/traces;不用 Grafana。
- **资源治理**:所有 Pod 强制 requests/limits,每个 namespace 配 ResourceQuota + LimitRange,JVM 统一 `-XX:MaxRAMPercentage=50`;全网关唯一 Ingress 暴露,ngrok 公网域名 + 内网 nip.io 并存。

## User Stories

1. As a 学习者, I want 在 Debian 12 上安装真实生产级的 k3s(而非 k3d 容器模拟), so that 学到宿主机层面的运维(containerd、systemd、存储、节点管理)
2. As a 学习者, I want 配置镜像加速(docker.io 国内 mirror + GHCR 镜像源), so that 国内网络下拉镜像不超时失败
3. As a 学习者, I want 安装 metrics-server 并用 `kubectl top` 观察 node/pod 资源, so that 资源预算与 limits 设置有真实数据支撑
4. As a 学习者, I want Argo CD 安装在 cicd namespace 并经 nip.io 域名访问 UI, so that 有 GitOps 控制面可视化管理全部应用
5. As a 学习者, I want 一个独立私有的 gitops 配置仓库承载全部部署清单, so that "线上跑什么版本"完全由它的 git log 回答,回滚 = revert commit
6. As a 学习者, I want app-of-apps 根 Application 监听 apps/ 挂载点, so that 提交一个子 Application 清单即被自动纳管,新增微服务成本标准化
7. As a 学习者, I want 中间件用社区 Helm Chart(values 覆盖)、自研微服务用 Kustomize base/overlay, so that 两种主流清单管理方式都在同一体系里得到练习
8. As a 学习者, I want middleware-test 部署 PostgreSQL(Deployment + PVC), so that 业务与 Nacos 都有持久化存储依赖且贴近企业形态
9. As a 学习者, I want middleware-test 部署 Redis(Deployment + PVC), so that 业务服务有缓存依赖可用
10. As a 学习者, I want Nacos 以单机模式 + pgsql 存储部署, so that 体验"无状态应用 + 外置数据库"的企业部署形态
11. As a 学习者, I want 中间件三件套逐一启用并验证连通性(控制台登录、PG 连接、Redis ping)后再接业务, so that 排障边界清晰、依赖顺序正确
12. As a 学习者, I want infra 层部署 otel-collector(Deployment ×1), so that 业务应用的 logs/metrics/traces 有一条统一上报通路
13. As a 学习者, I want OpenObserve 以 StatefulSet + PVC 部署, so that 练习有状态负载与数据生命周期管理(保留策略 logs 7d / traces 3d / metrics 15d)
14. As a 学习者, I want trace 先 100% 全采, so that 学习阶段任何链路问题都不漏
15. As a 学习者, I want 业务 Pod 只注入 OTLP 环境变量(otel-collector.infra:4317,gRPC)即接入可观测, so that 11 个跨语言服务零改造统一上报
16. As a 学习者, I want 11 个微服务从网关开始逐个(或分批)经 gitops Application 启用, so that 掌握按依赖顺序接入集群的方法
17. As a 学习者, I want 每个环境只有网关服务暴露 Ingress, so that 复刻"唯一入口"的企业边界形态,其余服务仅集群内可达
18. As a 学习者, I want Ingress 按域名路由且 host 集中配置(ngrok → test 网关,nip.io → 各面板), so that 将来加环境或换真域名只改 host、链路不变
19. As a 学习者, I want 所有 Pod 强制 requests/limits 且每个 namespace 配 ResourceQuota + LimitRange, so that 学会资源治理,防止单服务吃光 32G 内存
20. As a 学习者, I want JVM 服务统一 `-XX:MaxRAMPercentage=50`, so that Java 服务感知容器内存约束不 OOM
21. As a 学习者, I want 全部组件按资源预算落位(合计 ~14G / 32G,余量充足), so that 单节点不超卖、扩 dev 环境有余量
22. As a 学习者, I want 明文 Secret 直接提交私有 gitops 仓库(GHCR pull secret 配到 ServiceAccount 全局生效;PG/Redis/Nacos 密码;OpenObserve token), so that 先跑通体系,再以 Sealed Secrets 迁移作为第一个进阶实验
23. As a 学习者, I want 挑 1 个 Java 服务配 CPU HPA(min 1 / max 3), so that 练习弹性伸缩全链路
24. As a 学习者, I want local-path 存储(PVC 即宿主机目录), so that 不引入分布式存储复杂度也能跑通有状态负载
25. As a 学习者, I want 演练 GitOps 漂移场景(`kubectl delete deployment` 观察 selfHeal、`kubectl edit` 观察 drift 标记), so that 理解声明式集群的纠正与暴露机制
26. As a 学习者, I want overlays/dev 只放占位骨架、不建 Application 清单, so that 多环境能力结构上预留但不消耗任何集群资源
27. As a 开发者, I want 业务仓库只新增 Dockerfile 和一个构建 workflow 两个文件, so that 运维清单不混入业务仓库,变更关注点分离
28. As a 开发者, I want 只有 commit message 含 `[deploy]` 标记才触发构建, so that 日常提交不产生镜像、节省 CI 资源
29. As a 开发者, I want Actions 构建的镜像 tag = git short sha、推送 GHCR、并回写 gitops 仓库对应服务 overlay, so that 镜像与代码提交一一对应、发布历史天然可审计
30. As a 发布操作者, I want 通过 workflow_dispatch 手动输入已验证的镜像 tag 触发晋升回写(workflow 校验该 tag 存在于 GHCR), so that 生产(test)发版永不重新构建
31. As a 发布操作者, I want Argo CD 中手动点 SYNC 才真正上线(双确认的第二确认), so that 每次生产变更都是人工拍板、完全可见可控
32. As a 发布操作者, I want 每次发版有三重留痕(workflow run + Argo CD sync 记录 + gitops commit), so that 任何一次上线可完整审计
33. As a 发布操作者, I want 回滚 = revert gitops 仓库的 commit 或在 Argo CD 选历史 sync 版本重新 sync, so that 任何上线都能快速退回
34. As a 学习者, I want 11 个服务全部注册 Nacos 而非改用 k8s Service DNS, so that 保持已验证的 Spring Cloud Alibaba 调用链,学习焦点集中在 k8s 体系而非服务发现改造
35. As a 学习者, I want k8s Service DNS 只承担中间件访问, so that 两层服务发现(k8s 内部 / Nacos 业务)的职责边界清晰

## Implementation Decisions

遵循 4 个既有 ADR,不重新讨论其结论:

- **ADR 0001**:gitops 配置仓库(独立新建、私有)与业务仓库(11 个微服务各自原 repo)分离;业务仓库只加 Dockerfile + 构建 workflow;CI 用 git short sha 回写 gitops 仓库。
- **ADR 0002**:11 个服务全部注册 Nacos;Nacos 是强依赖,每环境独立一套;k8s Service DNS 只承担中间件访问。
- **ADR 0003**:`[deploy]` 标记构建门禁 + 晋升双确认;生产无任何全自动发布环节;单环境阶段标记只负责"产生镜像"。
- **ADR 0004**:k3s 而非 k3d;local-path 存储;PG 用 Deployment + PVC 而非 Operator。

**环境模型**:启用 biz-test(业务 namespace `biz-test` + 中间件 namespace `middleware-test`);预留 biz-dev(`biz-dev` + `middleware-dev`),启用与否的唯一开关 = gitops 仓库中是否存在对应 Argo CD Application 清单;dev overlay 只放占位骨架。

**gitops 仓库结构**:根 Application(app-of-apps)监听 apps/ 挂载点;其下分 argocd(Helm values)、middleware(base 公共 values + test/dev overlay + 三件套各自 Application)、infra(collector 与 OpenObserve 各一个 Application)、biz(Kustomize base 通用模板 + 每服务一个 overlay 目录只写差异(镜像 tag、端口、env、资源)+ 11 个 Application 清单);另有 secrets 目录存明文 Secret。

**bootstrap 顺序**(每步验证后再进下一步):k3s 安装(Traefik 保留)→ registries.yaml 镜像加速 → metrics-server → Argo CD(Helm,cicd namespace,nip.io Ingress)→ gitops 仓库初始化并提交根 Application → middleware-test 三件套逐一启用验证 → infra(otel-collector → OpenObserve,验证 OTLP 通路)→ biz-test 从网关开始逐个启用。本地工具:kubectl、helm、k9s。

**资源预算**(全网强制 requests/limits,每 namespace ResourceQuota + LimitRange):metrics-server 256M;Argo CD ~1G;otel-collector 256M;OpenObserve 1.5G;PG 512M;Redis 128M;Nacos 1G;11 个微服务 ~7G(Java 768M/个、Go/Python 128~256M/个,副本数 1);系统层 ~2G;合计 ~14G/32G。JVM 统一 `-XX:MaxRAMPercentage=50`。

**CI / 发布**:GitHub Actions;`[deploy]` 触发构建,tag = git short sha,推 GHCR,回写 gitops biz-test overlay;biz-test Application 手动同步;晋升 = workflow_dispatch 输入 tag → 校验 GHCR 存在 → 回写 test overlay → Argo CD 手动 SYNC。

**可观测**:业务 Pod 注入 OTLP 环境变量(otel-collector.infra:4317,gRPC);OpenObserve 保留策略 logs 7d / traces 3d / metrics 15d;trace 100% 全采;不使用 Grafana。

**密钥**:明文 Secret 提交私有 gitops 仓库;GHCR pull secret 配到 ServiceAccount 全 namespace 生效。

**Ingress**:按域名路由,host 字段集中配置;ngrok 域名 → test 网关,内网 nip.io → Argo CD / Nacos / OpenObserve 各面板。

**HPA**:挑 1 个 Java 服务,CPU 扩缩容 min 1 / max 3。

## Testing Decisions

- **唯一主缝(已与用户确认)**:gitops 仓库提交 → 集群外部可观察行为。所有验证都在这条缝上,不进组件内部扎针(不把 Pod 内部日志当验收、不 SSH 进容器探测)。五个验证视角:
  1. **GitOps 接管**:Argo CD Application 状态(Synced/Healthy)+ kubectl 只读查询;
  2. **HTTP 入口**:ngrok 网关域名返回业务响应,nip.io 面板(Argo CD/Nacos/OpenObserve)可访问;
  3. **可观测通路**:业务服务的 trace/log/metric 在 OpenObserve 查询结果中出现;
  4. **CI 门禁**:含 `[deploy]` 的 push → GHCR 出现 short sha 镜像 + gitops 仓库被回写;不含标记 → 两处都无变化;
  5. **发布晋升**:workflow_dispatch → test overlay 更新 → 手动 SYNC → 网关新版本生效;revert → 回滚;`kubectl delete deployment` → selfHeal 救回(演示场景);`kubectl edit` → drift 被标记。
- **好的测试** = 只断言外部可观察状态(域名响应、Application 状态、GHCR/Actions/gitops 三处留痕、OpenObserve 查询结果),不断言实现细节(Chart 版本、Pod 内部配置)。
- **验收点即 bootstrap 冒烟**:README §5 的"每步验证后再进下一步"就是主缝上的逐段验收清单(metrics-server → `kubectl top nodes`;三件套 → 控制台登录、PG 连接、Redis ping;infra → OTLP 上报通路;biz → 网关域名业务响应)。
- **先例**:本仓库无既有测试(从零搭建基础设施),bootstrap 冒烟清单即为先行范例,后续所有变更沿用同一主缝验证。

## Out of Scope

- biz-dev 环境的实际启用(只交付占位骨架;启用属进阶实验 6)
- 进阶实验清单全部内容:Sealed Secrets 密钥迁移、Longhorn、CloudNativePG Operator、Grafana 对接 OpenObserve、tail-sampling 降采样、多环境启用与 `[deploy]` 自动发布
- 全自动发布机制(Argo CD Image Updater、push 即部署)——与 ADR 0003 门禁冲突,明确否决
- 服务发现改造为 k8s Service DNS(ADR 0002 否决)
- 业务微服务自身的代码改造(业务仓库只加 Dockerfile + 构建 workflow)
- 真实重要数据的存放(local-path 无数据复制,节点故障即数据丢失)
- 企业级安全加固(RBAC 细化、网络策略、审计等未在方案中出现,不引入)

## Further Notes

- 服务器为 Debian 12,8C/32G,单节点;学习/研究用途,切勿放真实重要数据。
- 11 个微服务的 Nacos 注册与调用链已在开发环境全部跑通验证;本 spec 不改业务代码。
- 语言栈混合:Java(Spring Cloud Alibaba,Feign 按 Nacos 服务名负载均衡)、go-kratos、Python。
- GHCR 与 GitHub Actions 隐含业务仓库托管在 GitHub;gitops 配置仓库同为 GitHub 私有仓(Argo CD 需能拉取)。
