# k3s 单节点与 local-path 存储

学习环境选 k3s（真实生产级发行版，systemd 管理贴近生产）而非 k3d（容器模拟节点）；存储用 k3s 自带的 local-path（PVC 即宿主机目录）；PostgreSQL 用 Deployment + PVC 而非 Operator。

**Why:** 单节点上 Longhorn（分布式块存储）和 CloudNativePG（主备切换）的核心价值发挥不出来，只增加资源占用和维护负担；k3d 学不到宿主机层面的运维（containerd、存储、节点管理），与"完整体系"的学习目标不符。

**Considered:** k3d 多集群快速重建；Longhorn 练分布式存储；CNPG Operator 练 Operator 模式。三者都列为体系跑通后的进阶实验，不进主线，故否决。

**Consequences:** local-path 无数据复制，节点故障即数据丢失——学习环境可接受，切勿放真实重要数据；PVC 绑定宿主机路径，未来迁多节点时需迁移存储方案（进阶实验 2）。
