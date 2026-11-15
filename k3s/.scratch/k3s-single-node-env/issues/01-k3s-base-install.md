# 01: k3s 底座安装

**What to build:** 在 Debian 12 单节点(8C/32G)上装好真实生产级的 k3s 底座:Traefik 与 local-path 保持默认启用;配置镜像加速(docker.io 国内 mirror + GHCR 镜像源,重启 k3s 生效);安装 metrics-server;kubectl(远程 kubeconfig)、helm、k9s 本地工具就位。完成后有一个可操作的单节点集群,后续所有工单都部署在它之上。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `kubectl get nodes` 显示单节点 Ready;kubeconfig 已拷到本地,kubectl / helm / k9s 可用
- [ ] 用测试 Pod 拉 docker.io 公共镜像成功(镜像加速生效)
- [ ] `kubectl top nodes` 返回节点资源数据(metrics-server 生效)
- [ ] 部署一个测试 PVC 可绑定成功(local-path 生效);测试 Ingress 域名路由可达(Traefik 生效)
