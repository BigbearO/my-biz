# GitOps 配置仓库与业务仓库分离

业务仓库（11 个微服务）只保留 Dockerfile 和 GitHub Actions workflow，负责"代码 → 镜像"；所有部署清单集中在一个独立的私有 gitops 仓库，Argo CD 只监听它，负责"镜像 → 集群"。

**Why:** 让"线上跑什么版本"完全由 gitops 仓库的 git log 回答，回滚 = revert commit；业务仓库不混入运维清单，变更关注点分离。CI 构建后用 git short sha 回写 gitops 仓库，发布历史天然可审计。

**Considered:** 业务仓库各自维护部署清单（改一个中间件参数要动 11 个仓库）；全部塞进业务 monorepo（业务与集群配置耦合）。均在多仓库现状下增加协作成本，故否决。

**Consequences:** 新增微服务 = gitops 仓库加一个 Application 清单；任何人改集群状态都必须经 gitops 仓库，直接 kubectl 修改会被 Argo CD 漂移检测暴露。
