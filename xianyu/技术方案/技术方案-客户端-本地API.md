# 技术方案-客户端-本地API

> 来源：[《客户端监听.md》](../客户端监听.md) §2.11 本地 HTTP 接口与事件推送；[《客户端技术方案.md》](../客户端技术方案.md) §五 本地 API 接口清单（账号/商品同步/系统/日志四组）（内容重组收录，原文件保留未动）
> 关联：[需求-客户端-账号管理](../需求/需求-客户端-账号管理.md)

## 一、功能与职责（总）

### 1.1 功能定位

Python 本地服务对 Electron 渲染层暴露的**本地 HTTP API**（FastAPI 路由，`api/` 目录）。渲染层的账号管理页、设置页全部经这组接口读写本地服务（★日志页已取消，无对应接口）。

**状态获取一律轮询**：渲染层按需轮询 `/api/system/status`（监听数 / 版本号 / 云端连通状态 / 登录态）、`/api/sync/status/{account_id}`（同步进度与结果）、`/api/account/list`（账号状态）、`/api/logs/list`（日志）。**不设 SSE/推送通道**（原 `/api/events` 事件流与 `/api/system/health` 健康检查已裁定移除）。

### 1.2 职责边界

| 负责 | 不负责 |
|---|---|
| 本地 HTTP 路由（账号/同步/系统/日志四组）与参数校验 | 业务逻辑实现（由 `services/` 层完成，见同目录各功能点文档） |
| 请求鉴权（所有接口校验 `X-Local-Token`） | 云端 API（云端调用走 cloud_client，见 [技术方案-客户端-云端调用与容错](技术方案-客户端-云端调用与容错.md)） |
| 登录态 token 的落库（config 表）与读取——**Python 是 token 的唯一持有者**（Electron 登录成功后经 `/api/system/login` 交给 Python 保存，之后统一经 Python 取） | 云端管理后台的接口（WebView 内嵌，不属于本地 API）；Electron 侧的登录页与登录请求（登录实现参考 plus-ui，见 [需求-客户端-应用框架与启动登录](../需求/需求-客户端-应用框架与启动登录.md)） |
| 云端连通状态 / 登录态的状态汇聚（供 `/api/system/status` 返回） | 端口协商与进程守护（见 [技术方案-客户端-总体架构](技术方案-客户端-总体架构.md)、[技术方案-客户端-本地服务进程管理](技术方案-客户端-本地服务进程管理.md)） |

### 1.3 与其他模块的关系

| 对象 | 方向 | 交互方式 |
|---|---|---|
| Electron 渲染层 | 上游 | HTTP fetch 轮询（无推送通道） |
| Electron 主进程 | 上游 | 启动握手获知端口 + 进程句柄/TCP 探活；退出前 `/api/account/stop-all` |
| 账号服务 / 监听调度 / 商品同步 / 日志 | 下游 | 路由层转调对应 service |
| 云端客户端 | 下游 | 添加账号时同步上报云端；**统一从 config 表读 token 调云端**（token 由 Electron 登录后经 `/api/system/login` 交入） |

## 二、逻辑实现（分）

### 2.1 总体设计

- 基础地址：`http://127.0.0.1:{port}/api`（端口协商见 [技术方案-客户端-总体架构](技术方案-客户端-总体架构.md) §2.4）；
- 接口分四组：账号、同步、系统、日志；
- 只监听 `127.0.0.1`，不对外网暴露。

### 2.2 鉴权与安全

**所有请求必须携带 `X-Local-Token`**（Electron 启动时生成、经环境变量传给 Python），防止本机其他程序调用，**无免 token 例外**。

### 2.3 状态轮询约定

| 渲染层关注点 | 轮询接口 | 建议节奏 |
|---|---|---|
| 云端连通状态、登录态（401 过期） | `GET /api/system/status` | 页面驻留期间定时轮询（如 5s） |
| 账号列表 / 监听状态 | `GET /api/account/list` | 进入账号管理页与操作后刷新 |
| 同步进度与结果 | `GET /api/sync/status/{account_id}` | 同步触发后轮询至完成 |
（★日志无页面、无轮询接口——日志只落本地文件，见 [需求-客户端-日志](../../需求/需求-客户端-日志.md)）

## 四、接口设计

统一接口清单（基础地址 `http://127.0.0.1:{port}/api`，两份源清单已按较新口径合并）：

**账号管理**

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/account/list | GET | 账号列表（分页） |
| /api/account/add | POST | 添加账号（校验 Cookie + 拉取账号信息 + 上报云端） |
| /api/account/{id} | DELETE | 删除账号（监听中需前端先确认，服务端二次校验，A26） |
| /api/account/{id}/detail | GET | 详情（含完整 Cookie，脱敏由前端处理） |
| /api/account/{id}/start-listen | POST | 开始监听（上限校验 A6） |
| /api/account/{id}/stop-listen | POST | 停止监听 |
| /api/account/stop-all | POST | 全部停止（托盘 A35 / 退出 A38） |

**商品同步**

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/sync/items/{account_id} | POST | 触发全量同步（异步，重复点击返回 409） |
| /api/sync/status/{account_id} | GET | 同步状态与结果（新增 X / 更新 X） |

**系统**

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/system/status | GET | 监听数、版本号、**云端连通状态**、登录态（401 过期标记） |
| /api/system/login | POST | **接收 Electron 传来的登录 token 与 clientid 并写入 config 表**（Electron 登录成功后调用；Python 是二者唯一持有者） |
| /api/system/token | GET | **返回当前登录 token 与 clientid**（供 Electron 使用，如安装包下载请求头；本地 `X-Local-Token` 保护） |
| /api/system/logout | POST | 退出登录（清空 config 表 token） |
| /api/system/check-update | GET | 检查更新 |

~~**日志**~~ ★已取消

> 日志**只落本地文件、不提供管理页面**（见 [需求-客户端-日志](../../需求/需求-客户端-日志.md)），因此**不再提供 `/api/logs/list` / `clear` / `export` 三个接口**；需要排查时直接打开 `%APPDATA%/goodfish/logs/` 下的日志文件。

> **已裁定移除**：`/api/system/health`（免 token 健康检查）与 `/api/events`（SSE 事件流）不再提供——探活改用启动端口握手 + 进程句柄/TCP 探测，状态/进度/登录态一律由渲染层轮询获取。

## 五、异常与容错

| 异常场景 | 处理策略 |
|---|---|
| 本机其他程序未带 token 调用 | `X-Local-Token` 校验拒绝（token 由 Electron 启动时生成、经环境变量传给 Python） |
| Electron 主进程探活 | 不依赖 HTTP 探针：以启动握手端口 + 进程句柄为准，可辅以对本地端口 TCP 连通性探测 |
| 删除监听中的账号 | 前端先确认，服务端二次校验（先停监听再删，A26） |
| 同步重复触发 | 返回 409 |
| 添加账号时云端上报失败 | `/api/account/add` 返回错误提示"账号信息上报云端失败"；**本地不入库**——先云端上报、后本地入库的顺序保证两端账号一致（未上报成功的账号无法商品同步，见 [技术方案-云端-商品服务](技术方案-云端-商品服务.md) §2.2） |
| 云端返回 401（登录态过期） | cloud_client 删除本地 token，`/api/system/status` 返回登录态过期标记 → 渲染层轮询到后跳转登录页，不自动重登；监听保持运行（A27） |
| 云端连续失败 | `/api/system/status` 的云端连通状态置为"不可用"（A21）→ 渲染层轮询到后提示"仅监听不回复" |

## 六、配置项

| 配置键 | 默认值 | 说明 |
|---|---|---|
| auth_token | 空 | 管理后台登录 token：Electron 登录成功后经 `/api/system/login` 写入；**Python 为唯一持有者**，其他方（含 Electron）经 `/api/system/token` 获取 |
| clientid | 空 | 客户端 ID：与 token 同源经 `/api/system/login` 写入；★必须与 token **一起带出**——网关会校验 `clientid` 请求头与 token 是否匹配，缺失或不匹配将被拒绝 |
| cloud_backend_url | 打包写死 | 云端后台地址（`/api/system/status` 据此报云端连通状态） |
| X-Local-Token | 随机生成 | 本地 API 鉴权 token，Electron 启动时生成、经环境变量传给 Python（不入配置表） |
