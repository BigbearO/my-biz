// Package gitlog 实现多仓库 git 分支列举与提交日志查询的 MCP 工具逻辑。
package gitlog

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"
	"golang.org/x/sync/errgroup"
)

const (
	defaultMaxCommits = 200
	hardMaxCommits    = 1000
	repoTimeout       = 10 * time.Second
)

// ---------- list_multi_repo_branches ----------

type ListBranchesArgs struct {
	RepoPaths []string `json:"repo_paths" jsonschema:"本地 git 仓库路径数组,必填"`
}

type BranchInfo struct {
	Repo           string   `json:"repo"`
	CurrentBranch  string   `json:"current_branch,omitempty"`
	LocalBranches  []string `json:"local_branches,omitempty"`
	RemoteBranches []string `json:"remote_branches,omitempty"`
	Error          string   `json:"error,omitempty"`
}

type ListBranchesResult struct {
	Repos []BranchInfo `json:"repos"`
}

// ListBranches 是 list_multi_repo_branches 工具的 handler。
func ListBranches(ctx context.Context, _ *mcp.CallToolRequest, args ListBranchesArgs) (*mcp.CallToolResult, ListBranchesResult, error) {
	repos, err := normalizePaths(args.RepoPaths)
	if err != nil {
		return nil, ListBranchesResult{}, err
	}

	results := make([]BranchInfo, len(repos))
	g, gctx := errgroup.WithContext(ctx)
	for i, repo := range repos {
		g.Go(func() error {
			rctx, cancel := context.WithTimeout(gctx, repoTimeout)
			defer cancel()
			results[i] = listBranchesOne(rctx, repo)
			return nil // 单仓库失败不拖垮整体
		})
	}
	_ = g.Wait()
	return nil, ListBranchesResult{Repos: results}, nil
}

func listBranchesOne(ctx context.Context, repo string) BranchInfo {
	info := BranchInfo{Repo: repo}
	if err := checkRepo(ctx, repo); err != nil {
		info.Error = err.Error()
		return info
	}

	cur, err := runGit(ctx, repo, "rev-parse", "--abbrev-ref", "HEAD")
	if err != nil {
		info.Error = err.Error()
		return info
	}
	info.CurrentBranch = strings.TrimSpace(cur)

	if local, err := runGit(ctx, repo, "branch", "--format=%(refname:short)"); err == nil {
		info.LocalBranches = splitLines(local)
	}
	if remote, err := runGit(ctx, repo, "branch", "-r", "--format=%(refname:short)"); err == nil {
		for _, b := range splitLines(remote) {
			if strings.Contains(b, "HEAD") { // 过滤 origin/HEAD 这类符号引用
				continue
			}
			info.RemoteBranches = append(info.RemoteBranches, b)
		}
	}
	return info
}

// ---------- get_multi_repo_git_log ----------

type GetLogArgs struct {
	RepoPaths  []string `json:"repo_paths" jsonschema:"本地 git 仓库路径数组,必填"`
	Since      string   `json:"since,omitempty" jsonschema:"起始时间,支持 RFC3339 / 2006-01-02 / 2006/01/02 / 相对时间如 24h 7d 2w"`
	Until      string   `json:"until,omitempty" jsonschema:"截止时间,格式同 since"`
	Author     string   `json:"author,omitempty" jsonschema:"按提交人筛选,匹配 name 或 email,支持子串"`
	Branch     string   `json:"branch,omitempty" jsonschema:"目标分支,对所有仓库生效;不传则使用各仓库当前分支"`
	MaxCommits int      `json:"max_commits,omitempty" jsonschema:"每个仓库返回的最大提交数,默认 200,上限 1000"`
}

type Commit struct {
	Hash    string `json:"hash"`
	Short   string `json:"short_hash"`
	Author  string `json:"author"`
	Email   string `json:"email"`
	Date    string `json:"date"` // RFC3339
	Subject string `json:"subject"`
}

type RepoLog struct {
	Repo    string   `json:"repo"`
	Branch  string   `json:"branch,omitempty"`
	Commits []Commit `json:"commits,omitempty"`
	Error   string   `json:"error,omitempty"`
}

type GetLogResult struct {
	Repos []RepoLog `json:"repos"`
}

// GetLogs 是 get_multi_repo_git_log 工具的 handler。
func GetLogs(ctx context.Context, _ *mcp.CallToolRequest, args GetLogArgs) (*mcp.CallToolResult, GetLogResult, error) {
	repos, err := normalizePaths(args.RepoPaths)
	if err != nil {
		return nil, GetLogResult{}, err
	}
	since, err := parseTime(args.Since)
	if err != nil {
		return nil, GetLogResult{}, err
	}
	until, err := parseTime(args.Until)
	if err != nil {
		return nil, GetLogResult{}, err
	}
	if !since.IsZero() && !until.IsZero() && until.Before(since) {
		return nil, GetLogResult{}, fmt.Errorf("until(%s) 早于 since(%s),请检查时间范围", args.Until, args.Since)
	}

	maxCommits := clampMax(args.MaxCommits)
	results := make([]RepoLog, len(repos))
	g, gctx := errgroup.WithContext(ctx)
	for i, repo := range repos {
		g.Go(func() error {
			rctx, cancel := context.WithTimeout(gctx, repoTimeout)
			defer cancel()
			results[i] = getLogOne(rctx, repo, args, since, until, maxCommits)
			return nil
		})
	}
	_ = g.Wait()
	return nil, GetLogResult{Repos: results}, nil
}

func getLogOne(ctx context.Context, repo string, args GetLogArgs, since, until time.Time, maxCommits int) RepoLog {
	res := RepoLog{Repo: repo}
	if err := checkRepo(ctx, repo); err != nil {
		res.Error = err.Error()
		return res
	}

	// 用 \x1f 分隔字段、\x1e 分隔记录,避免提交内容中的分隔符/换行导致解析错乱
	gitArgs := []string{
		"log",
		"--no-merges",
		"--pretty=format:%H%x1f%h%x1f%an%x1f%ae%x1f%aI%x1f%s%x1e",
		"--max-count=" + strconv.Itoa(maxCommits),
	}
	if !since.IsZero() {
		gitArgs = append(gitArgs, "--since="+since.Format(time.RFC3339))
	}
	if !until.IsZero() {
		gitArgs = append(gitArgs, "--until="+until.Format(time.RFC3339))
	}
	if author := strings.TrimSpace(args.Author); author != "" {
		gitArgs = append(gitArgs, "--author="+author)
	}

	branch := strings.TrimSpace(args.Branch)
	if branch != "" {
		if _, err := runGit(ctx, repo, "rev-parse", "--verify", "--quiet", branch+"^{commit}"); err != nil {
			res.Error = fmt.Sprintf("分支 %q 在该仓库不存在", branch)
			return res
		}
		gitArgs = append(gitArgs, branch)
		res.Branch = branch
	} else if cur, err := runGit(ctx, repo, "rev-parse", "--abbrev-ref", "HEAD"); err == nil {
		res.Branch = strings.TrimSpace(cur)
	}

	out, err := runGit(ctx, repo, gitArgs...)
	if err != nil {
		res.Error = err.Error()
		return res
	}
	res.Commits = parseLog(out)
	return res
}

// parseLog 解析 \x1f/\x1e 分隔的 git log 输出,并过滤噪声提交。
func parseLog(out string) []Commit {
	var commits []Commit
	for _, rec := range strings.Split(out, "\x1e") {
		fields := strings.Split(strings.Trim(rec, "\n"), "\x1f")
		if len(fields) < 6 {
			continue
		}
		subject := cleanSubject(fields[5])
		if subject == "" || isNoiseSubject(subject) {
			continue
		}
		commits = append(commits, Commit{
			Hash:    strings.TrimSpace(fields[0]),
			Short:   strings.TrimSpace(fields[1]),
			Author:  strings.TrimSpace(fields[2]),
			Email:   strings.TrimSpace(fields[3]),
			Date:    strings.TrimSpace(fields[4]),
			Subject: subject,
		})
	}
	return commits
}

// cleanSubject 折叠首尾空白与内部换行。
func cleanSubject(s string) string {
	return strings.Join(strings.Fields(s), " ")
}

// isNoiseSubject 过滤 merge/fixup/squash 等噪声提交。
func isNoiseSubject(s string) bool {
	lower := strings.ToLower(s)
	return strings.HasPrefix(lower, "merge ") ||
		strings.HasPrefix(lower, "fixup!") ||
		strings.HasPrefix(lower, "squash!")
}

// ---------- 公共辅助 ----------

// runGit 在指定仓库下执行 git 子命令,参数直接走 argv 不过 shell,杜绝注入。
func runGit(ctx context.Context, repo string, args ...string) (string, error) {
	full := append([]string{"-C", repo}, args...)
	cmd := exec.CommandContext(ctx, "git", full...)
	var out, errBuf bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &errBuf
	if err := cmd.Run(); err != nil {
		return "", fmt.Errorf("git %s 失败: %w: %s", strings.Join(args, " "), err, strings.TrimSpace(errBuf.String()))
	}
	return out.String(), nil
}

// checkRepo 校验路径存在且是 git 工作区。
func checkRepo(ctx context.Context, repo string) error {
	st, err := os.Stat(repo)
	if err != nil {
		return fmt.Errorf("路径不可访问: %w", err)
	}
	if !st.IsDir() {
		return fmt.Errorf("路径不是目录")
	}
	out, err := runGit(ctx, repo, "rev-parse", "--is-inside-work-tree")
	if err != nil || strings.TrimSpace(out) != "true" {
		return fmt.Errorf("不是 git 仓库")
	}
	return nil
}

// normalizePaths 清洗路径:去空白、展开 ~、转绝对路径、去重。
func normalizePaths(paths []string) ([]string, error) {
	seen := make(map[string]bool, len(paths))
	var out []string
	for _, p := range paths {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		p = expandHome(p)
		if abs, err := filepath.Abs(p); err == nil {
			p = abs
		}
		p = filepath.Clean(p)
		if !seen[p] {
			seen[p] = true
			out = append(out, p)
		}
	}
	if len(out) == 0 {
		return nil, fmt.Errorf("repo_paths 不能为空")
	}
	return out, nil
}

func expandHome(p string) string {
	if p == "~" || strings.HasPrefix(p, "~/") || strings.HasPrefix(p, `~\`) {
		if home, err := os.UserHomeDir(); err == nil {
			return filepath.Join(home, strings.TrimLeft(p[1:], `/\`))
		}
	}
	return p
}

var relativeTimeRe = regexp.MustCompile(`^(\d+)\s*([hdw])$`)

// parseTime 宽容解析时间:RFC3339、常见日期格式、相对时间(24h/7d/2w)。
func parseTime(s string) (time.Time, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return time.Time{}, nil
	}
	layouts := []string{
		time.RFC3339,
		"2006-01-02 15:04:05",
		"2006-01-02",
		"2006/01/02 15:04:05",
		"2006/01/02",
		"20060102",
	}
	for _, layout := range layouts {
		if t, err := time.ParseInLocation(layout, s, time.Local); err == nil {
			return t, nil
		}
	}
	if m := relativeTimeRe.FindStringSubmatch(strings.ToLower(s)); m != nil {
		n, _ := strconv.Atoi(m[1])
		var d time.Duration
		switch m[2] {
		case "h":
			d = time.Duration(n) * time.Hour
		case "d":
			d = time.Duration(n) * 24 * time.Hour
		case "w":
			d = time.Duration(n) * 7 * 24 * time.Hour
		}
		return time.Now().Add(-d), nil
	}
	return time.Time{}, fmt.Errorf("无法解析时间 %q,支持 RFC3339 / 2006-01-02 / 2006/01/02 / 相对时间如 24h 7d 2w", s)
}

func clampMax(n int) int {
	if n <= 0 {
		return defaultMaxCommits
	}
	if n > hardMaxCommits {
		return hardMaxCommits
	}
	return n
}

func splitLines(s string) []string {
	var out []string
	for _, line := range strings.Split(s, "\n") {
		if line = strings.TrimSpace(line); line != "" {
			out = append(out, line)
		}
	}
	return out
}
