package server

import (
	"context"
	"errors"
	"sync"

	"mcp-git-log/internal/gitlog"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

// ListBranchesArgs 是 list_repo_branches 的输入参数。
type ListBranchesArgs struct {
	RepoPaths []string `json:"repo_paths" jsonschema:"本地 git 仓库路径数组（必填）"`
}

// RepoBranchResult 是单个仓库的分支查询结果。
type RepoBranchResult struct {
	Repo           string          `json:"repo"`
	CurrentBranch  string          `json:"current_branch" jsonschema:"当前分支名；detached HEAD 时为空"`
	DetachedHead   bool            `json:"detached_head" jsonschema:"是否处于 detached HEAD 状态"`
	LocalBranches  []gitlog.Branch `json:"local_branches" jsonschema:"本地分支列表"`
	RemoteBranches []gitlog.Branch `json:"remote_branches" jsonschema:"远程跟踪分支列表"`
}

// ListBranchesOutput 是 list_repo_branches 的结构化输出。
type ListBranchesOutput struct {
	Results []RepoBranchResult `json:"results" jsonschema:"查询成功的仓库分支列表"`
	Errors  []RepoError        `json:"errors" jsonschema:"查询失败的仓库及原因（不影响其他仓库）"`
}

// HandleListBranches 是 list_repo_branches 的处理器。
func HandleListBranches(ctx context.Context, req *mcp.CallToolRequest, args ListBranchesArgs) (*mcp.CallToolResult, ListBranchesOutput, error) {
	if len(args.RepoPaths) == 0 {
		return nil, ListBranchesOutput{}, errors.New("repo_paths is required and must not be empty")
	}

	out := ListBranchesOutput{Errors: []RepoError{}}
	results := make([]*RepoBranchResult, len(args.RepoPaths))
	var mu sync.Mutex
	sem := make(chan struct{}, maxConcurrency)
	var wg sync.WaitGroup

	for i, path := range args.RepoPaths {
		wg.Add(1)
		go func(i int, repoPath string) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			res, err := queryRepoBranches(ctx, repoPath)
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

// queryRepoBranches 查询单个仓库的分支信息。
func queryRepoBranches(ctx context.Context, repoPath string) (*RepoBranchResult, error) {
	if repoPath == "" {
		return nil, errors.New("empty repo path")
	}
	if err := gitlog.ValidateRepo(ctx, repoPath); err != nil {
		return nil, err
	}
	b, err := gitlog.ListBranches(ctx, repoPath)
	if err != nil {
		return nil, err
	}
	return &RepoBranchResult{
		Repo:           repoPath,
		CurrentBranch:  b.CurrentBranch,
		DetachedHead:   b.DetachedHead,
		LocalBranches:  b.Local,
		RemoteBranches: b.Remote,
	}, nil
}
