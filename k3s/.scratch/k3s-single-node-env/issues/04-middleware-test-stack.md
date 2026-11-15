# 04: middleware-test 三件套

**What to build:** 在 middleware-test namespace 部署一套 PostgreSQL(Deployment + PVC)、Redis(Deployment + PVC)、Nacos(单机模式,pgsql 存储):中间件用社区 Helm Chart 改 values;Nacos 接 PG;密码等明文 Secret 提交 gitops 仓库;三件套逐一启用、每件验证连通后再进下一件。完成后业务微服务具备完整中间件依赖(Nacos 是全站服务发现的强依赖)。

**Blocked by:** 03(gitops 仓库 + app-of-apps)

**Status:** ready-for-agent

- [ ] 三个 Application(Synced/Healthy),资源在预算内(PG 512M / Redis 128M / Nacos 1G limit)
- [ ] Nacos 控制台经 nip.io 可登录;注册一个测试服务名在列表可见
- [ ] 集群内业务视角可连 PG(建表/查询成功)、Redis ping 通(k8s Service DNS 承担中间件访问)
- [ ] Nacos 重启后服务列表与配置不丢(pgsql 存储生效)
