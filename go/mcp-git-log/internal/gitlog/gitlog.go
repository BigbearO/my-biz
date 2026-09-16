// Package gitlog 封装对本机 git 命令的调用与输出解析。
package gitlog

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os/exec"
	"strconv"
	"strings"
	"time"
)

const (
	fieldSep   = "\x1f" // 单元分隔符：字段之间（commit message 不会包含此控制字符）
	execTimeout = 30 * time.Second
)

// Commit 表示一条结构化的 git 提交记录。
type Commit struct {
	Hash    string `json:"hash" jsonschema:"完整 commit hash"`
	Short   string `json:"short" jsonschema:"短 hash"`
	Author  string `json:"author" jsonschema:"提交人"`
	Email   string `json:"email" jsonschema:"提交人邮箱"`
	Date    string `json:"date" jsonschema:"提交时间（ISO 8601）"`
	Subject string `json:"subject" jsonschema:"提交标题"`
}

// Options 是查询 git log 的过滤条件。
type Options struct {
	Branch string // 分支名 / tag / commit-ish，空表示当前 HEAD
	Since  string
	Until  string
	Author string
	Limit  int // 最大返回条数
}

// ErrNotAGitRepo 表示路径不是 git 仓库。
var ErrNotAGitRepo = errors.New("not a git repository")

// ValidateRepo 检查路径是否为可用的 git 仓库（含裸仓库）。
func ValidateRepo(ctx context.Context, repoPath string) error {
	out, err := runGit(ctx, []string{"-C", repoPath, "rev-parse", "--git-dir"})
	if err != nil {
		if strings.Contains(err.Error(), "not a git repository") {
			return fmt.Errorf("%w: %s", ErrNotAGitRepo, repoPath)
		}
		return err
	}
	if len(bytes.TrimSpace(out)) == 0 {
		return fmt.Errorf("%w: %s", ErrNotAGitRepo, repoPath)
	}
	return nil
}

// Log 执行 git log 并返回解析后的 commit 列表。
func Log(ctx context.Context, repoPath string, opt Options) ([]Commit, error) {
	commitish := opt.Branch
	if commitish == "" {
		commitish = "HEAD"
	}
	// git refname 不允许以 "-" 开头，拒绝以防止被 git 当作选项解析
	if strings.HasPrefix(commitish, "-") {
		return nil, fmt.Errorf("invalid branch name %q", opt.Branch)
	}

	args := []string{"-C", repoPath, "log", commitish}
	args = append(args, filterArgs(opt)...)
	args = append(args,
		"-n", strconv.Itoa(opt.Limit),
		"--date=iso8601-strict",
		"--pretty=format:%H"+fieldSep+"%h"+fieldSep+"%an"+fieldSep+"%ae"+fieldSep+"%ad"+fieldSep+"%s\x1e",
	)

	out, err := runGit(ctx, args)
	if err != nil {
		return nil, err
	}
	commits := ParseLogOutput(out)
	if commits == nil {
		commits = []Commit{}
	}
	return commits, nil
}

// CountMatching 返回满足同样过滤条件（不含 Limit）的 commit 总数。
func CountMatching(ctx context.Context, repoPath string, opt Options) (int, error) {
	commitish := opt.Branch
	if commitish == "" {
		commitish = "HEAD"
	}
	if strings.HasPrefix(commitish, "-") {
		return 0, fmt.Errorf("invalid branch name %q", opt.Branch)
	}
	args := []string{"-C", repoPath, "rev-list", "--count", commitish}
	args = append(args, filterArgs(opt)...)

	out, err := runGit(ctx, args)
	if err != nil {
		return 0, err
	}
	n, err := strconv.Atoi(strings.TrimSpace(string(out)))
	if err != nil {
		return 0, fmt.Errorf("parse rev-list count: %w", err)
	}
	return n, nil
}

// filterArgs 组装 since/until/author 过滤参数（值拼接在选项名后，无注入风险）。
func filterArgs(opt Options) []string {
	var args []string
	if opt.Since != "" {
		args = append(args, "--since="+opt.Since)
	}
	if opt.Until != "" {
		args = append(args, "--until="+opt.Until)
	}
	if opt.Author != "" {
		args = append(args, "--author="+opt.Author)
	}
	return args
}

// ParseLogOutput 解析 %x1f（字段）/ %x1e（记录）分隔的 git log 输出。
func ParseLogOutput(data []byte) []Commit {
	var commits []Commit
	for _, rec := range bytes.Split(data, []byte("\x1e")) {
		rec = bytes.TrimSpace(rec)
		if len(rec) == 0 {
			continue
		}
		fields := strings.Split(string(rec), fieldSep)
		if len(fields) < 6 {
			continue
		}
		commits = append(commits, Commit{
			Hash:    fields[0],
			Short:   fields[1],
			Author:  fields[2],
			Email:   fields[3],
			Date:    fields[4],
			Subject: fields[5],
		})
	}
	return commits
}

// runGit 执行 git 命令并返回 stdout；失败时把 stderr 信息合并进错误。
func runGit(ctx context.Context, args []string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(ctx, execTimeout)
	defer cancel()
	var stdout, stderr bytes.Buffer
	cmd := exec.CommandContext(ctx, "git", args...)
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		msg := strings.TrimSpace(stderr.String())
		if msg == "" {
			msg = err.Error()
		}
		return nil, errors.New("git: " + msg)
	}
	return stdout.Bytes(), nil
}
