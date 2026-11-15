# 02: Argo CD 控制面

**What to build:** 用 Helm 把 Argo CD 安装到 cicd namespace(平台层,不分环境),配 Ingress 走内网 nip.io 域名,初始 admin 凭据处置完毕。完成后有可视化的 GitOps 控制面,浏览器可登录管理全部应用。

**Blocked by:** 01(k3s 底座安装)

**Status:** ready-for-agent

- [ ] Argo CD 各组件在 cicd namespace 运行正常(kubectl 只读确认 Healthy)
- [ ] 浏览器经 nip.io 域名登录 Argo CD UI 成功
- [ ] Argo CD 资源占用在预算内(~1G limit,`kubectl top` 可见)
