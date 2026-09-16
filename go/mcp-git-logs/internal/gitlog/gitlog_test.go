package gitlog

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
	"time"
)

// initRepo 在临时目录初始化一个 git 仓库并制造若干提交。
func initRepo(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	run := func(args ...string) {
		cmd := exec.Command("git", append([]string{"-C", dir}, args...)...)
		if out, err := cmd.CombinedOutput(); err != nil {
			t.Fatalf("git %v: %v\n%s", args, err, out)
		}
	}
	commit := func(msg string) {
		if err := os.WriteFile(filepath.Join(dir, "f.txt"), []byte(msg), 0o644); err != nil {
			t.Fatal(err)
		}
		run("add", ".")
		run("commit", "-m", msg)
	}

	run("init", "-b", "main")
	run("config", "user.name", "Tester")
	run("config", "user.email", "tester@example.com")
	commit("feat: first commit")
	commit("fix: second commit")
	commit("fixup! fix: second commit")

	run("checkout", "-b", "dev")
	commit("feat: dev branch commit")
	run("checkout", "main")
	return dir
}

func TestGetLogs(t *testing.T) {
	repo := initRepo(t)

	_, result, err := GetLogs(context.Background(), nil, GetLogArgs{RepoPaths: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Repos) != 1 {
		t.Fatalf("expect 1 repo, got %d", len(result.Repos))
	}
	r := result.Repos[0]
	if r.Error != "" {
		t.Fatalf("unexpected error: %s", r.Error)
	}
	if r.Branch != "main" {
		t.Fatalf("expect branch main, got %q", r.Branch)
	}
	// 3 个提交中 fixup! 噪声应被过滤
	if len(r.Commits) != 2 {
		t.Fatalf("expect 2 commits after noise filter, got %d: %+v", len(r.Commits), r.Commits)
	}
	if r.Commits[0].Subject != "fix: second commit" {
		t.Fatalf("unexpected first commit: %+v", r.Commits[0])
	}
	if r.Commits[0].Author != "Tester" || r.Commits[0].Email != "tester@example.com" {
		t.Fatalf("unexpected author info: %+v", r.Commits[0])
	}
}

func TestGetLogsWithBranch(t *testing.T) {
	repo := initRepo(t)

	_, result, err := GetLogs(context.Background(), nil, GetLogArgs{
		RepoPaths: []string{repo},
		Branch:    "dev",
	})
	if err != nil {
		t.Fatal(err)
	}
	r := result.Repos[0]
	if r.Error != "" {
		t.Fatalf("unexpected error: %s", r.Error)
	}
	if len(r.Commits) != 3 {
		t.Fatalf("expect 3 commits on dev, got %d", len(r.Commits))
	}

	// 不存在的分支:该仓库记入 error
	_, result, err = GetLogs(context.Background(), nil, GetLogArgs{
		RepoPaths: []string{repo},
		Branch:    "no-such-branch",
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.Repos[0].Error == "" {
		t.Fatal("expect error for non-existent branch")
	}
}

func TestGetLogsFilters(t *testing.T) {
	repo := initRepo(t)

	// author 过滤
	_, result, err := GetLogs(context.Background(), nil, GetLogArgs{
		RepoPaths: []string{repo},
		Author:    "Nobody",
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Repos[0].Commits) != 0 {
		t.Fatalf("expect 0 commits for unknown author, got %d", len(result.Repos[0].Commits))
	}

	// since 过滤:未来起点应为空
	_, result, err = GetLogs(context.Background(), nil, GetLogArgs{
		RepoPaths: []string{repo},
		Since:     time.Now().Add(time.Hour).Format(time.RFC3339),
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Repos[0].Commits) != 0 {
		t.Fatalf("expect 0 commits with future since, got %d", len(result.Repos[0].Commits))
	}

	// 非法时间格式应返回错误
	_, _, err = GetLogs(context.Background(), nil, GetLogArgs{
		RepoPaths: []string{repo},
		Since:     "not-a-time",
	})
	if err == nil {
		t.Fatal("expect error for invalid since")
	}

	// 非 git 路径:记入 error,不报错
	_, result, err = GetLogs(context.Background(), nil, GetLogArgs{
		RepoPaths: []string{repo, t.TempDir()},
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.Repos[1].Error == "" {
		t.Fatal("expect error for non-git path")
	}
}

func TestListBranches(t *testing.T) {
	repo := initRepo(t)

	_, result, err := ListBranches(context.Background(), nil, ListBranchesArgs{RepoPaths: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	info := result.Repos[0]
	if info.Error != "" {
		t.Fatalf("unexpected error: %s", info.Error)
	}
	if info.CurrentBranch != "main" {
		t.Fatalf("expect current branch main, got %q", info.CurrentBranch)
	}
	if len(info.LocalBranches) != 2 {
		t.Fatalf("expect 2 local branches, got %v", info.LocalBranches)
	}
}

func TestParseTime(t *testing.T) {
	cases := []string{
		"2026-09-01",
		"2026/09/01",
		"2026-09-01 10:00:00",
		"2026-09-01T10:00:00+08:00",
		"20260901",
		"7d", "24h", "2w",
		"",
	}
	for _, c := range cases {
		if _, err := parseTime(c); err != nil {
			t.Errorf("parseTime(%q) failed: %v", c, err)
		}
	}
	if _, err := parseTime("yesterday morning"); err == nil {
		t.Error("expect error for unparseable time")
	}
}

func TestClampMax(t *testing.T) {
	if clampMax(0) != defaultMaxCommits {
		t.Error("0 should fall back to default")
	}
	if clampMax(99999) != hardMaxCommits {
		t.Error("should clamp to hard max")
	}
	if clampMax(50) != 50 {
		t.Error("valid value should pass through")
	}
}
