# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

> 锚定说明:项目根是 `k3s/`。git 仓库根 my-biz 是个人多项目混合仓,不在 domain docs 读取范围内。

## Before exploring, read these

- **`GLOSSARY.md`**:位于项目根(k3s/)
- **`docs/adr/`**:位于项目根(k3s/),读取与当前工作区域相关的 ADR

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates them lazily when terms or decisions actually get resolved.

## File structure

Single-context repo:

```
k3s/
├── GLOSSARY.md
├── AGENTS.md
├── docs/
│   ├── adr/
│   │   ├── 0001-gitops-repo-separation.md
│   │   ├── 0002-all-services-register-nacos.md
│   │   ├── 0003-release-gating.md
│   │   └── 0004-k3s-local-path-single-node.md
│   └── agents/          ← 本配置目录
└── .scratch/            ← 本地 markdown issue tracker
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `GLOSSARY.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0007 (event-sourced orders), but worth reopening because…_
