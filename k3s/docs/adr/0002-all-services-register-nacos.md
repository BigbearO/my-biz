# 全服务注册 Nacos

11 个跨语言微服务（Spring Cloud Alibaba / go-kratos / Python）的服务发现统一走 Nacos，而不是让 Go/Python 服务直接用 k8s Service DNS。

**Why:** 技术上 k8s Service DNS 更云原生且零 SDK 依赖，但 Feign 按 Nacos 服务名负载均衡是 Spring Cloud Alibaba 生态的既有惯例，且该方案已在开发环境全部跑通验证。改造成 k8s Service 模式意味着重写 Java 侧服务调用链，学习目标（k8s 体系）不在服务发现这一层。

**Considered:** Java 走 Nacos + Go/Python 走 k8s Service DNS（混合，最"标准"）；全部走 k8s Service（最纯粹）。两者都要求改造已验证的调用链，收益只在理念纯度，故否决。

**Consequences:** Nacos 是强依赖——它挂了全站服务发现失效；每个环境必须独立部署一套 Nacos；k8s 的服务发现能力（Service/CoreDNS）在这套体系里只承担中间件访问。
