# 05: infra 可观测链路

**What to build:** 在 infra namespace(平台层)部署 otel-collector(Deployment ×1)与 OpenObserve(StatefulSet + PVC):保留策略 logs 7d / traces 3d / metrics 15d;trace 先 100% 全采;OpenObserve token 明文 Secret 入 gitops 仓库;验证 OTLP 上报通路。完成后统一可观测的接收端可用,业务服务后续零改造接入。

**Blocked by:** 03(gitops 仓库 + app-of-apps;与 04 并行)

**Status:** ready-for-agent

- [ ] 两个 Application Synced/Healthy,资源在预算内(collector 256M / OpenObserve 1.5G limit)
- [ ] OpenObserve 面板经 nip.io 可登录
- [ ] 用测试客户端向 collector 发 OTLP gRPC(:4317)trace → OpenObserve 查询可见
- [ ] 同一通路验证 log 与 metric 均可查;保留策略配置生效
