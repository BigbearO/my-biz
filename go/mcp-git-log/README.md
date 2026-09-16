# mcp-git-log

一个 stdio MCP Server（Go 单二进制），用于查询多个本地 git 仓库的提交记录，供 Agent（Claude Desktop / Cursor / CodeBuddy 等）调用。

依赖官方 SDK [modelcontextprotocol/go-sdk](https://github.com/modelcontextprotocol/go-sdk) 构建，运行时依赖本机 `git` 命令（需在 PATH 中）。

## 工具

### 1. `list_repo_branches`

查询多个本地 git 仓库的分支列表。当用户未明确指定分支、或需要确认分支名时，Agent 先调用本工具，向用户确认后再调用 `get_multi_repo_git_log`。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `repo_paths` | string[] | 是 | 本地 git 仓库路径数组 |

返回（每仓库）：`current_branch`（当前分支，detached HEAD 时为空）、`detached_head`、`local_branches`、`remote_branches`（形如 `origin/master`）。

### 2. `get_multi_repo_git_log`

查询多个本地 git 仓库的提交记录，返回结构化 commit 列表，自动过滤 typo / format 类噪音提交。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `repo_paths` | string[] | 是 | 本地 git 仓库路径数组 |
| `branch` | string | 否 | 分支名，支持本地分支、远程跟踪分支（如 `origin/main`）、tag。缺省用各仓库当前 HEAD |
| `since` | string | 否 | 起始时间，支持 `2026-08-01`、`2 weeks ago` 等 git 日期格式 |
| `until` | string | 否 | 结束时间，格式同 `since` |
| `author` | string | 否 | 按提交人过滤（正则模糊匹配） |
| `max_commits` | int | 否 | 每仓库返回上限，默认 100，硬上限 1000 |

返回（每仓库）：

```json
{
  "repo": "e:/project/my-biz/go/my",
  "commits": [
    {
      "hash": "a11240dd...",
      "short": "a11240d",
      "author": "bigbear",
      "email": "x@example.com",
      "date": "2026-08-15T19:30:02+08:00",
      "subject": "feat.xxx"
    }
  ],
  "filtered_count": 2,
  "truncated": true,
  "total_available": 248
}
```

- `filtered_count`：被过滤掉的 typo / format 类噪音提交条数（匹配规则：subject 含 `typo(s)`；以 `format`/`fmt`/`gofmt`/`clang-format`/`prettier` 开头；或提及格式化工具 / code style）
- `truncated` / `total_available`：结果因超过 `max_commits` 被截断时，标记 `truncated=true` 并给出满足条件的总数，Agent 可据此缩小时间范围重查
- 单个仓库查询失败（路径不存在、不是 git 仓库、分支不存在等）不影响其他仓库，失败信息归入 `errors` 字段

## 构建

```bash
go build -o mcp-git-log.exe .
# Linux/macOS
go build -o mcp-git-log .
```

## 测试

```bash
go test ./...
```

## 客户端接入

### Claude Desktop / Claude Code

`claude_desktop_config.json`：

```json
{
  "mcpServers": {
    "git-log": {
      "command": "e:/project/my-biz/go/mcp-git-log/mcp-git-log.exe"
    }
  }
}
```

### Cursor / 通用 mcp.json

```json
{
  "mcpServers": {
    "git-log": {
      "command": "e:/project/my-biz/go/mcp-git-log/mcp-git-log.exe"
    }
  }
}
```

### 手动验证

```bash
npx @modelcontextprotocol/inspector e:/project/my-biz/go/mcp-git-log/mcp-git-log.exe
```

## 实现说明

- stdio 模式下 stdout 是 MCP 协议通道，所有日志走 stderr
- `git log` 使用 `%x1f`（字段）/`%x1e`（记录）控制字符分隔输出，避免 commit message 含特殊字符导致解析错乱
- 时间统一 `--date=iso8601-strict`
- 每条 git 子进程命令带 30s 超时；多仓库并发查询（上限 8 并发）
- 分支名等参数直接作为独立 argv 传给 `git`，不经 shell，无命令注入风险；拒绝以 `-` 开头的分支名防止被 git 解析为选项
