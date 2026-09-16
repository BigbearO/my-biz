package main

import (
	"context"
	"encoding/json"
	"os/exec"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

// TestSmoke 通过真实 MCP stdio 协议做端到端测试:
// 构建二进制 -> 客户端连接 -> 列出工具 -> 调用 get_multi_repo_git_log。
func TestSmoke(t *testing.T) {
	bin := filepath.Join(t.TempDir(), "mcp-git-logs")
	if runtime.GOOS == "windows" {
		bin += ".exe"
	}
	if out, err := exec.Command("go", "build", "-o", bin, ".").CombinedOutput(); err != nil {
		t.Fatalf("build failed: %v\n%s", err, out)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	client := mcp.NewClient(&mcp.Implementation{Name: "smoke-test", Version: "0"}, nil)
	session, err := client.Connect(ctx, &mcp.CommandTransport{Command: exec.Command(bin)}, nil)
	if err != nil {
		t.Fatalf("connect failed: %v", err)
	}
	defer session.Close()

	tools, err := session.ListTools(ctx, nil)
	if err != nil {
		t.Fatalf("list tools failed: %v", err)
	}
	names := map[string]bool{}
	for _, tool := range tools.Tools {
		names[tool.Name] = true
	}
	for _, want := range []string{"list_multi_repo_branches", "get_multi_repo_git_log"} {
		if !names[want] {
			t.Fatalf("tool %q not registered, got %v", want, names)
		}
	}

	// 项目目录本身位于 git 工作区内,直接用它做真实调用
	res, err := session.CallTool(ctx, &mcp.CallToolParams{
		Name: "get_multi_repo_git_log",
		Arguments: map[string]any{
			"repo_paths":  []string{"."},
			"max_commits": 5,
		},
	})
	if err != nil {
		t.Fatalf("call tool failed: %v", err)
	}
	if res.IsError {
		t.Fatalf("tool returned error: %+v", res.Content)
	}
	data, err := json.Marshal(res.StructuredContent)
	if err != nil {
		t.Fatal(err)
	}
	var parsed struct {
		Repos []struct {
			Repo    string `json:"repo"`
			Branch  string `json:"branch"`
			Commits []struct {
				Hash    string `json:"hash"`
				Subject string `json:"subject"`
			} `json:"commits"`
			Error string `json:"error"`
		} `json:"repos"`
	}
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("structured content not decodable: %v\n%s", err, data)
	}
	if len(parsed.Repos) != 1 || parsed.Repos[0].Error != "" {
		t.Fatalf("unexpected result: %s", data)
	}
	if len(parsed.Repos[0].Commits) > 5 {
		t.Fatalf("max_commits not respected: %s", data)
	}
	t.Logf("branch=%s commits=%d", parsed.Repos[0].Branch, len(parsed.Repos[0].Commits))
}
