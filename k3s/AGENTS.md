# k3s 单节点学习环境

本目录(`k3s/`)是本项目的项目根:在 Debian 12 单节点上用 k3s 搭建 GitOps + 微服务 + 统一可观测的完整学习环境。注意:git 仓库根是上级的 `my-biz`(个人多项目混合仓),本文件与 `docs/agents/` 均锚定 `k3s/`,domain 文档也只覆盖本项目。

## Agent skills

### Issue tracker

本地 markdown:issue 与 spec 以文件形式存放在 `.scratch/<feature-slug>/`。See `docs/agents/issue-tracker.md`.

### Triage labels

五个默认角色标签(needs-triage / needs-info / ready-for-agent / ready-for-human / wontfix)。See `docs/agents/triage-labels.md`.

### Domain docs

single-context:`GLOSSARY.md` 与 `docs/adr/` 位于 k3s/ 根。See `docs/agents/domain.md`.
