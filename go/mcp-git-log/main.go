// mcp-git-log 是一个 stdio MCP Server，
// 对外暴露两个工具：list_repo_branches 和 get_multi_repo_git_log。
package main

import (
	"context"
	"log"

	"mcp-git-log/internal/server"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

func main() {
	// 注意：stdio 模式下 stdout 是 MCP 协议通道，所有日志必须走 stderr。
	s := mcp.NewServer(&mcp.Implementation{
		Name:    "mcp-git-log",
		Version: "1.0.0",
	}, nil)

	server.Register(s)

	if err := s.Run(context.Background(), &mcp.StdioTransport{}); err != nil {
		log.Fatalf("server exited: %v", err)
	}
}
