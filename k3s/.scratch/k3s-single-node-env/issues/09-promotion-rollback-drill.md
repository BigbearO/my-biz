# 09: 晋升与回滚演练

**What to build:** 实现 promotion workflow:workflow_dispatch 手动输入已验证镜像 tag → 校验该 tag 存在于 GHCR → 回写 gitops test overlay;再在 Argo CD 手动点 SYNC 才生效(双确认的第二确认)。生产(test)发版全程不重新构建。随后完成回滚与漂移演练。完成后"晋升 → 双确认 → 三重留痕 → 回滚"的发版闭环可完整复演。

**Blocked by:** 06(CI 门禁流水线)、07(网关全链路上线)

**Status:** ready-for-agent

- [ ] workflow_dispatch 输入已存在的 tag → 校验通过 → test overlay 回写 commit;输入不存在的 tag → workflow 失败且不回写
- [ ] Argo CD 显示 OutOfSync → 手动 SYNC → 新版本生效;三重留痕齐全(workflow run + Argo CD sync 记录 + gitops commit)
- [ ] revert 那次回写 commit → SYNC 后集群回到旧版本(完整回滚演练)
- [ ] 漂移演练:`kubectl delete deployment` 观察 selfHeal 救回(临时开启自动同步或用测试应用演示);`kubectl edit` 观察 Argo CD 标记 drift
