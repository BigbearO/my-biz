# 10: 资源治理 + HPA

**What to build:** 为每个 namespace(biz-test / middleware-test / cicd / infra)配 ResourceQuota + LimitRange,复核全部已部署负载的 requests/limits 与资源预算一致(合计 ~14G / 32G,余量充足);挑 1 个 Java 服务配 CPU HPA(min 1 / max 3)。完成后单节点资源治理与弹性伸缩闭环。

**Blocked by:** 08(其余 10 个微服务上线)

**Status:** ready-for-agent

- [ ] 各 namespace 的 ResourceQuota 生效:超出配额的负载创建被拒
- [ ] LimitRange 兜底生效:未写 resources 的测试 Pod 获得默认值/被拒(与所选策略一致);存量业务 Pod 全部有 requests/limits
- [ ] `kubectl top` 汇总占用与预算吻合(不超卖,余量充足)
- [ ] 对目标 Java 服务施压 → HPA 扩容至 >1 副本可见;撤压 → 缩回;扩缩容全程经网关域名访问不中断
