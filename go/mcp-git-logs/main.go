package main

import (
	"context"
	"log/slog"
	"os"

	"github.com/modelcontextprotocol/go-sdk/mcp"

	"mcp-git-logs/internal/gitlog"
)

func main() {
	// stdio 模式下 stdout 只能传输 MCP 协议帧,业务日志必须输出到 stderr
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stderr, nil)))

	server := mcp.NewServer(&mcp.Implementation{
		Name:    "mcp-git-logs",
		Version: "0.1.0",
	}, nil)

	mcp.AddTool(server, &mcp.Tool{
		Name: "list_multi_repo_branches",
		Description: "列出多个本地 git 仓库的分支情况(当前分支、本地分支、远程分支)。" +
			"如需跨多个仓库查询提交日志,建议先调用本工具了解各仓库分支情况," +
			"与用户确认目标分支后再调用 get_multi_repo_git_log。",
	}, gitlog.ListBranches)

	mcp.AddTool(server, &mcp.Tool{
		Name: "get_multi_repo_git_log",
		Description: "批量查询多个本地 git 仓库的提交记录,支持时间范围、作者、分支过滤。" +
			"branch 参数对所有仓库生效;不传则使用各仓库当前分支;" +
			"若不同仓库需要查不同分支,请分多次调用。" +
			"建议先用较窄的 since/until 或 author 缩小范围,不够再放宽。" +
			"默认排除 merge/fixup/squash 等噪声提交。",
	}, gitlog.GetLogs)

	if err := server.Run(context.Background(), &mcp.StdioTransport{}); err != nil {
		slog.Error("server exited", "err", err)
		os.Exit(1)
	}
}
