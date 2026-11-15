# 08: 其余 10 个微服务上线

**What to build:** 用 biz 的 Kustomize base 模板复制网关模式,分批启用其余 10 个微服务:每服务一个 overlay,只写差异(镜像 tag、端口、环境变量、资源规格);资源按预算落位(Java 768M/个、Go/Python 128~256M/个,副本数 1,JVM 统一 `-XX:MaxRAMPercentage=50`);每服务一个 Application 清单,分批启用、每批验证后再进下一批。完成后 11 个服务全部注册 Nacos、跨语言调用链打通,网关仍是唯一入口。

**Blocked by:** 07(网关全链路上线)

**Status:** ready-for-agent

- [ ] 11 个 Application 全部 Synced/Healthy;每服务一个 overlay,仅含差异项
- [ ] Nacos 服务列表出现全部 11 个服务(Java / go-kratos / Python 三种语言齐)
- [ ] 经网关发起的典型调用链跨 Java↔Go↔Python,业务响应正确
- [ ] OpenObserve 中可见跨服务 trace 树;除网关外无任何服务暴露 Ingress
