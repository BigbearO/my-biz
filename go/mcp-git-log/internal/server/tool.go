// Package server 注册 MCP 工具并编排多仓库并发查询。
package server

import (
	"context"
	"errors"
	"fmt"
	"sync"

	"mcp-git-log/internal/filter"
	"mcp-git-log/internal/gitlog"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

const (
	defaultMaxCommits = 100  // 每仓库默认返回上限
	hardMaxCommits    = 1000 // 每仓库硬上限
	maxConcurrency    = 8    // 并发访问仓库/子进程数上限
)

// GitLogArgs 是 get_multi_repo_git_log 的输入参数。
type GitLogArgs struct {
	RepoPaths  []string `json:"repo_paths" jsonschema:"本地 git 仓库路径数组（必填）"`
	Branch     string   `json:"branch,omitempty" jsonschema:"分支名，支持本地分支、远程跟踪分支（如 origin/main）或 tag。缺省时使用各仓库当前 HEAD"`
	Since      string   `json:"since,omitempty" jsonschema:"起始时间，支持 2026-08-01、2 weeks ago 等 git 日期格式"`
	Until      string   `json:"until,omitempty" jsonschema:"结束时间，格式同 since"`
	Author     string   `json:"author,omitempty" jsonschema:"按提交人过滤（正则模糊匹配）"`
	MaxCommits int      `json:"max_commits,omitempty" jsonschema:"每仓库返回的 commit 条数上限，默认 100，最大 1000"`
}

// RepoLogResult 是单个仓库的查询结果。
type RepoLogResult struct {
	Repo           string         `json:"repo" jsonschema:"仓库路径"`
	Commits        []gitlog.Commit `json:"commits" jsonschema:"结构化 commit 列表（已过滤 typo/format 噪音提交）"`
	FilteredCount  int            `json:"filtered_count" jsonschema:"被过滤掉的噪音提交条数"`
	Truncated      bool           `json:"truncated" jsonschema:"结果是否因超过 max_commits 而被截断"`
	TotalAvailable int            `json:"total_available" jsonschema:"满足过滤条件的 commit 总数（仅在截断时返回）"`
}

// RepoError 描述单个仓库的查询失败原因。
type RepoError struct {
	Repo  string `json:"repo"`
	Error string `json:"error"`
}

// GitLogOutput 是 get_multi_repo_git_log 的结构化输出。
type GitLogOutput struct {
	Results []RepoLogResult `json:"results" jsonschema:"查询成功的仓库结果"`
	Errors  []RepoError     `json:"errors" jsonschema:"查询失败的仓库及原因（不影响其他仓库）"`
}

// HandleGitLog 是 get_multi_repo_git_log 的处理器。
func HandleGitLog(ctx context.Context, req *mcp.CallToolRequest, args GitLogArgs) (*mcp.CallToolResult, GitLogOutput, error) {
	if len(args.RepoPaths) == 0 {
		return nil, GitLogOutput{}, errors.New("repo_paths is required and must not be empty")
	}
	limit := normalizeLimit(args.MaxCommits)

	out := GitLogOutput{Errors: []RepoError{}}
	results := make([]*RepoLogResult, len(args.RepoPaths))
	var mu sync.Mutex
	sem := make(chan struct{}, maxConcurrency)
	var wg sync.WaitGroup

	for i, path := range args.RepoPaths {
		wg.Add(1)
		go func(i int, repoPath string) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			res, err := queryRepoLog(ctx, repoPath, args, limit)
			if err != nil {
				mu.Lock()
				out.Errors = append(out.Errors, RepoError{Repo: repoPath, Error: err.Error()})
				mu.Unlock()
				return
			}
			results[i] = res
		}(i, path)
	}
	wg.Wait()

	for _, r := range results {
		if r != nil {
			out.Results = append(out.Results, *r)
		}
	}
	return nil, out, nil
}

// queryRepoLog 查询单个仓库：git log -> 噪音过滤 -> 截断信息。
func queryRepoLog(ctx context.Context, repoPath string, args GitLogArgs, limit int) (*RepoLogResult, error) {
	if repoPath == "" {
		return nil, errors.New("empty repo path")
	}
	if err := gitlog.ValidateRepo(ctx, repoPath); err != nil {
		return nil, err
	}

	opt := gitlog.Options{
		Branch: args.Branch,
		Since:  args.Since,
		Until:  args.Until,
		Author: args.Author,
		Limit:  limit,
	}
	commits, err := gitlog.Log(ctx, repoPath, opt)
	if err != nil {
		return nil, err
	}

	res := &RepoLogResult{Repo: repoPath, Commits: []gitlog.Commit{}}
	for _, c := range commits {
		if filter.IsNoise(c.Subject) {
			res.FilteredCount++
			continue
		}
		res.Commits = append(res.Commits, c)
	}

	// 只有当返回条数达到上限时才可能发生截断，此时再查总数
	if len(commits) == limit {
		if total, err := gitlog.CountMatching(ctx, repoPath, opt); err == nil {
			res.TotalAvailable = total
			res.Truncated = total > limit
		}
		// 总数查询失败不阻塞结果返回
	}
	return res, nil
}

// normalizeLimit 归一化 max_commits 参数。
func normalizeLimit(n int) int {
	if n <= 0 {
		return defaultMaxCommits
	}
	if n > hardMaxCommits {
		return hardMaxCommits
	}
	return n
}

// limitHint 返回写入工具描述的条数限制说明。
func limitHint() string {
	return fmt.Sprintf("max_commits 默认 %d，硬上限 %d", defaultMaxCommits, hardMaxCommits)
}
