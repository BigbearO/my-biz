# Goodfish Client

阶段 1 至阶段 4 的客户端基座：Electron 负责窗口与本地进程管理，Vue3 提供账号管理页面，Python FastAPI 提供仅监听 `127.0.0.1` 的本地 API，以 Windows DPAPI 与 AES-256-GCM 保护本地 Cookie，并在保存或手动校验时调用闲鱼协议验证登录态。

## 目录

```text
electron/    Electron 主进程、preload、Python 进程管理
src/         Vue3 渲染层
python/      FastAPI 本地服务、SQLite 迁移与测试
scripts/     开发启动辅助脚本
```

## 初始化

### Python

```powershell
cd python
C:\path\to\python.exe -m venv .venv
.venv\Scripts\python.exe -m pip install -e ".[dev]"
```

### Node

```powershell
cd ..
npm install
```

## 开发运行

```powershell
npm run dev
```

如需指定 Python 解释器：

```powershell
$env:GOODFISH_PYTHON_EXE="C:\path\to\python.exe"
npm run dev
```

## 验证

```powershell
npm run build
npm run test:python
```

## 本地数据

运行数据写入 `%APPDATA%\goodfish`：

```text
data/goodfish.db
logs/local-service.log
logs/electron.log
runtime.json
master.key
```

本地 API 端口由 Python 绑定 `127.0.0.1:0` 后经 stdout 握手行通知 Electron；`runtime.json` 仅作为握手失败时的兜底。所有本地 API 均要求 `X-Local-Token`，该 token 由 Electron 每次启动随机生成，只通过进程环境传递，不写入数据库或日志。

## 账号管理

本地账号管理提供以下接口：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/accounts` | 返回非保密账号列表 |
| POST | `/api/accounts` | 新增账号并加密 Cookie |
| PUT | `/api/accounts/{id}` | 编辑昵称、备注，可选重录 Cookie |
| POST | `/api/accounts/{id}/verify` | 手动校验已保存 Cookie |
| DELETE | `/api/accounts/{id}` | 删除 idle 状态账号 |

新增账号只需提交 Cookie 与备注。协议验证成功后自动从 Cookie 提取 `goodfish_id`（`unb` 优先，`munb` 兜底）与 `tracknick` 昵称，按 `goodfish_id` 去重并加密入库。重录 Cookie 会再次验证，且新 Cookie 必须属于原账号；手动校验失败只把账号标记为无效并更新 `last_check_at`，不删除账号、不清空或覆盖密文。网络与未知协议错误不会误判为无效。

账号响应不包含 Cookie。`goodfish_id` 创建后不可修改；非 idle 账号禁止删除、替换 Cookie 或手动校验，为监听阶段预留一致性与安全边界。协议实现记录于 `python/xianyu/UPSTREAM.md`，并保留上游 AGPL-3.0 许可证文本。

## Cookie 安全存储

`master.key` 使用当前 Windows 用户的 DPAPI `CurrentUser` 作用域保护，文件创建后不再重写。Cookie 使用 AES-256-GCM 加密后落库，存储格式为：

```text
base64(nonce + ciphertext + tag)
```

加密绑定了账户 Cookie 的固定关联数据，nonce 每次加密随机生成。Cookie 明文只存在于加解密调用的局部生命周期内，不写入日志、API 响应、异常信息或除 `goodfish_account.cookie` 以外的持久化字段。

启动时会校验主密钥和既有 Cookie 密文。校验失败只把 `/api/system/status` 的 `credentialState` 标记为 `undecryptable`，同时用 `cookieWritable` 区分“主密钥不可用”和“存量 Cookie 密文损坏”：前者不能新增或重录 Cookie，但允许查看、编辑资料和删除账号；后者可以在编辑页重录 Cookie 恢复。恢复过程不会删除、清空或覆盖 `master.key`、其他数据库记录或 Cookie 密文。生产环境要求 Windows 用户配置文件已加载，否则 DPAPI 会显式失败，不会降级为明文存储。
