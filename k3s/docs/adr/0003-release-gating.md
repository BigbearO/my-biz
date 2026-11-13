# 发布门禁：[deploy] 标记 + 晋升双确认

CI 触发与发布解耦为两级门禁：commit message 含 `[deploy]` 标记才构建镜像并回写 gitops 仓库（无标记不构建）；环境上线采用镜像晋升——workflow_dispatch 手动选择已验证的镜像 tag 回写部署清单，再在 Argo CD 手动 SYNC 才生效（双确认）。生产环境不存在任何全自动发布环节。

**Why:** 学习目标是掌握可控的发布过程而非全自动流水线；三重留痕（workflow run + Argo CD sync 记录 + git commit）满足完整发版的审计诉求；标记机制同时省 CI 资源——日常提交不产生镜像。

**Considered:** push 即自动部署所有环境（对生产不可接受）；Argo CD Image Updater 自动追新 tag（链路变魔法、发布动作不可见）。均与"生产只能手动"的门禁要求冲突，故否决。

**Consequences:** 单环境（biz-test）阶段，标记只负责"产生镜像"，上线仍需手动 SYNC；将来启用 biz-dev 时，同一机制无需改造即升级为"标记即发布 dev"，晋升流程保留给生产。
