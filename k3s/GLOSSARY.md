# GLOSSARY

- **环境（Environment）**：一套相互隔离的 namespace 组合（biz + middleware），由 gitops 仓库中对应 Application 清单的存在与否决定其启停。当前启用 biz-test，预留 biz-dev。
- **晋升（Promotion）**：把已在 GHCR 中、经过验证的镜像 tag 直接部署到下一环境，不重新构建。
- **双确认（Double Confirmation）**：生产发版的两次人工动作——workflow_dispatch 触发晋升回写 + Argo CD 手动 SYNC。
- **发布标记（Deploy Marker）**：commit message 中的 `[deploy]` 标识，是 CI 构建镜像并回写部署清单的唯一触发条件。
- **网关（Gateway）**：每个环境唯一暴露 Ingress 的微服务，环境内其余服务仅集群内可达。
- **平台层（Platform Layer）**：cicd（Argo CD）与 infra（otel-collector、OpenObserve）两组不区分环境的组件。
