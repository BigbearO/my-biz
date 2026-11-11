# bootstrap.yaml 本地引导配置(始终本地,不下发):
#   app    应用标识
#   nacos  注册与配置中心连接
#   trace  OTLP(链路 + 日志转发共用端点)
#   log    日志级别
#   env 环境变量(前缀 WIND_)优先级最高,如 WIND_TRACE_ENDPOINT
app:
  project: "{{.Project}}"
  appId: "{{lower .Service}}"
  name: "{{lower .Service}}-service"
  version: v1.0.0
  env: dev

nacos:
  addr: 127.0.0.1:8848
  namespace: dev
  group: DEFAULT_GROUP
  dataId: "{{.Project}}-{{lower .Service}}"   # 远端只放业务配置段(server/data 等)
  timeoutMs: 5000

trace:
  endpoint: 127.0.0.1:4317   # OTLP gRPC;留空则不启用链路/日志上报
  sampler: 1.0
  insecure: true

log:
  level: info
