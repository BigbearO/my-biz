package server

import (
	"fmt"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

// Register 在 MCP Server 上注册全部工具。
//
// 工具描述会直接影响 Agent 的调用决策：
// 引导 Agent 在分支不明确时先调 list_repo_branches，确认后再调 get_multi_repo_git_log。
func Register(s *mcp.Server) {
	mcp.AddTool(s, &mcp.Tool{
		Name: "get_multi_repo_git_log",
		Description: fmt.Sprintf(
			"查询多个本地 git 仓库的提交记录，返回结构化 commit 列表（hash/作者/时间/标题），"+
				"并自动过滤 typo、format 类噪音提交。支持按分支、时间区间、提交人过滤。"+
				"每仓库返回条数受 max_commits 限制（%s），被截断时结果中会标记 truncated=true 并附上 total_available 总数。"+
				"若不确定仓库有哪些分支，建议先调用 list_repo_branches 向用户确认后再传入 branch 参数。"+
				"某个仓库查询失败不影响其他仓库，失败信息会归入 errors 字段。", limitHint()),
	}, HandleGitLog)

	mcp.AddTool(s, &mcp.Tool{
		Name: "list_repo_branches",
		Description: "查询多个本地 git 仓库的分支列表（当前分支、本地分支、远程分支）。" +
			"当用户未明确指定分支、或需要确认分支名时，先调用本工具，" +
			"确认后再调用 get_multi_repo_git_log 并传入 branch 参数。",
	}, HandleListBranches)
}
