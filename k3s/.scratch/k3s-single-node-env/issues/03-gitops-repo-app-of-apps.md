# 03: gitops 仓库 + app-of-apps

**What to build:** 新建独立私有 gitops 配置仓库并按 spec 结构初始化:apps/ 挂载点(argocd / middleware / infra / biz)、root-app、secrets 目录;middleware 与 infra 的 Application 清单骨架、biz 的 Kustomize base 与 test overlay 骨架;dev overlay 只放占位骨架(不建 Application、不消耗集群资源)。提交根 Application 让 Argo CD 接管,再用一个测试子 Application 验证自动纳管。完成后"git 提交 = 集群意愿"的主缝通路打通——这是全体系唯一的状态入口。

**Blocked by:** 02(Argo CD 控制面)

**Status:** ready-for-agent

- [ ] Argo CD 中根 Application Synced/Healthy,其下子 Application 随清单自动出现
- [ ] 提交一个测试子 Application → Argo CD 自动出现并可同步;删除该清单 → 应用随之移除(环境开关语义成立)
- [ ] 集群中不存在任何 dev 环境资源(dev 仅占位骨架)
- [ ] 仓库结构与 spec 的布局一致(base/overlay 分层、secrets 目录、每服务一个 Application 清单的约定就位)
