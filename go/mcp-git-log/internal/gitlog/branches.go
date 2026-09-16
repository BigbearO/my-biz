package gitlog

import (
	"context"
	"strings"
)

// Branch 表示一个本地或远程分支。
type Branch struct {
	Name   string `json:"name" jsonschema:"分支名（远程分支形如 origin/main）"`
	IsHead bool   `json:"is_head" jsonschema:"是否为当前所在分支"`
}

// Branches 描述一个仓库的分支全貌。
type Branches struct {
	CurrentBranch string   `json:"current_branch" jsonschema:"当前分支名；detached HEAD 时为空"`
	DetachedHead  bool     `json:"detached_head" jsonschema:"是否处于 detached HEAD 状态"`
	Local         []Branch `json:"local_branches"`
	Remote        []Branch `json:"remote_branches"`
}

// ListBranches 返回仓库的本地/远程分支及当前分支。
// 实现：git for-each-ref，%(HEAD) 对当前分支输出 "*"，
// refname 不允许包含空格，因此用空格分隔即可安全解析。
func ListBranches(ctx context.Context, repoPath string) (*Branches, error) {
	out, err := runGit(ctx, []string{
		"-C", repoPath,
		"for-each-ref",
		"--format=%(refname) %(HEAD)",
		"refs/heads", "refs/remotes",
	})
	if err != nil {
		return nil, err
	}
	return ParseBranches(string(out)), nil
}

// ParseBranches 解析 for-each-ref 的输出。
func ParseBranches(data string) *Branches {
	b := &Branches{Local: []Branch{}, Remote: []Branch{}}
	for _, line := range strings.Split(data, "\n") {
		fields := strings.Fields(strings.TrimSpace(line))
		if len(fields) == 0 {
			continue
		}
		ref := fields[0]
		isHead := len(fields) > 1 && fields[1] == "*"
		switch {
		case strings.HasPrefix(ref, "refs/heads/"):
			name := strings.TrimPrefix(ref, "refs/heads/")
			b.Local = append(b.Local, Branch{Name: name, IsHead: isHead})
			if isHead {
				b.CurrentBranch = name
			}
		case strings.HasPrefix(ref, "refs/remotes/"):
			name := strings.TrimPrefix(ref, "refs/remotes/")
			// 跳过 origin/HEAD 这类符号引用
			if strings.HasSuffix(name, "/HEAD") {
				continue
			}
			b.Remote = append(b.Remote, Branch{Name: name, IsHead: isHead})
		}
	}
	b.DetachedHead = b.CurrentBranch == ""
	return b
}
