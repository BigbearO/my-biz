# Goodfish Client

阶段 1 与阶段 2 的客户端基座：Electron 负责窗口与本地进程管理，Vue3 提供账号管理页面，Python FastAPI 提供仅监听 `127.0.0.1` 的本地 API，并以 Windows DPAPI 与 AES-256-GCM 保护本地 Cookie。

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

## Cookie 安全存储

`master.key` 使用当前 Windows 用户的 DPAPI `CurrentUser` 作用域保护，文件创建后不再重写。Cookie 使用 AES-256-GCM 加密后落库，存储格式为：

```text
base64(nonce + ciphertext + tag)
```

加密绑定了账户 Cookie 的固定关联数据，nonce 每次加密随机生成。Cookie 明文只存在于加解密调用的局部生命周期内，不写入日志、API 响应、异常信息或除 `goodfish_account.cookie` 以外的持久化字段。

启动时会校验主密钥和既有 Cookie 密文。校验失败只把 `/api/system/status` 的 `credentialState` 标记为 `undecryptable`，不会删除、清空或覆盖 `master.key`、数据库记录和 Cookie 密文；需要用户重新录入 Cookie 后才能恢复。生产环境要求 Windows 用户配置文件已加载，否则 DPAPI 会显式失败，不会降级为明文存储。
