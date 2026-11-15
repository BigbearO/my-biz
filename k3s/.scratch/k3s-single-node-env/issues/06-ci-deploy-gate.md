# 06: CI 门禁流水线

**What to build:** 给网关业务仓库只新增 Dockerfile 与构建 workflow 两个文件,实现发布标记门禁:commit message 含 `[deploy]` 才构建镜像(tag = git short sha)推送 GHCR,并回写 gitops 仓库 biz test overlay 的镜像 tag;不含标记则不构建、不推送、不回写。需要 GHCR 推送凭据与 gitops 仓库回写凭据(deploy key 或 PAT)。完成后"代码 → 镜像 → 部署清单"的第一段留痕链路可用(上线仍需 Argo CD 手动 SYNC,属工单 09 的双确认)。

**Blocked by:** 03(gitops 仓库 + app-of-apps;与 04 / 05 并行)

**Status:** ready-for-agent

- [ ] 含 `[deploy]` 的 push → Actions run 成功,GHCR 出现 short sha tag 的镜像
- [ ] gitops 仓库出现回写 commit(overlay 镜像 tag 更新),run 与 commit 互相可追溯
- [ ] 不含标记的 push → 无 run、无镜像、无回写(门禁两处零变化)
- [ ] 业务仓库除 Dockerfile 与 workflow 外无其他新增文件(运维清单不混入业务仓)
