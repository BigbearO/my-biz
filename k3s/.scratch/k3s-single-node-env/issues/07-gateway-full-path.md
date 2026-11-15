# 07: 网关全链路上线

**What to build:** 把网关服务经 gitops Application 部署到 biz-test(第一个业务微服务,竖切打通全链路):镜像取自工单 06 的 GHCR short sha;GHCR pull secret 配到 ServiceAccount 全局生效;业务 Pod 注入 OTLP 环境变量(otel-collector.infra:4317,gRPC);Ingress 按域名路由,ngrok 公网域名指向网关(host 集中配置)。完成后:公网域名 → 业务响应 → Nacos 注册 → OpenObserve 链路数据,一条完整路径全部可验证。

**Blocked by:** 04(middleware-test 三件套)、05(infra 可观测链路)、06(CI 门禁流水线)

**Status:** ready-for-agent

- [ ] 网关 Application Synced/Healthy,经 ngrok 公网域名访问返回业务响应
- [ ] 网关服务出现在 Nacos 服务列表(注册生效)
- [ ] 网关产生的 trace/log 在 OpenObserve 可查(OTLP 通路端到端打通)
- [ ] kubectl 只读确认:网关是 biz-test 中唯一暴露 Ingress 的服务
