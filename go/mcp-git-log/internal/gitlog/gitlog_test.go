package gitlog

import (
	"reflect"
	"testing"
)

func TestParseLogOutput(t *testing.T) {
	input := "h1\x1fs1\x1fa1\x1fe1\x1f2026-09-01T10:00:00+08:00\x1ffeat: add login\x1e" +
		"h2\x1fs2\x1fa2\x1fe2\x1f2026-09-02T11:00:00+08:00\x1ffix: typo in docs\x1e"

	got := ParseLogOutput([]byte(input))
	want := []Commit{
		{Hash: "h1", Short: "s1", Author: "a1", Email: "e1", Date: "2026-09-01T10:00:00+08:00", Subject: "feat: add login"},
		{Hash: "h2", Short: "s2", Author: "a2", Email: "e2", Date: "2026-09-02T11:00:00+08:00", Subject: "fix: typo in docs"},
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("ParseLogOutput() = %+v, want %+v", got, want)
	}
}

func TestParseLogOutputEmpty(t *testing.T) {
	if got := ParseLogOutput(nil); len(got) != 0 {
		t.Errorf("ParseLogOutput(nil) = %+v, want empty", got)
	}
	if got := ParseLogOutput([]byte("\n \n")); len(got) != 0 {
		t.Errorf("ParseLogOutput(blank) = %+v, want empty", got)
	}
}

func TestParseLogOutputMultilineSubjectSafety(t *testing.T) {
	// 即使 subject 中出现 %x1e/%x1f 之外的字符也不会解析错乱；
	// git log 的 %s 只输出首行，这里验证字段数不足时安全跳过。
	got := ParseLogOutput([]byte("only-one-field"))
	if len(got) != 0 {
		t.Errorf("ParseLogOutput(malformed) = %+v, want empty", got)
	}
}

func TestParseBranches(t *testing.T) {
	input := "refs/heads/master *\n" +
		"refs/heads/dev \n" +
		"refs/remotes/origin/master \n" +
		"refs/remotes/origin/dev \n" +
		"refs/remotes/origin/HEAD \n"

	got := ParseBranches(input)
	want := &Branches{
		CurrentBranch: "master",
		DetachedHead:  false,
		Local:         []Branch{{Name: "master", IsHead: true}, {Name: "dev"}},
		Remote:        []Branch{{Name: "origin/master"}, {Name: "origin/dev"}},
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("ParseBranches() = %+v, want %+v", got, want)
	}
}

func TestParseBranchesDetached(t *testing.T) {
	input := "refs/heads/master \nrefs/remotes/origin/master \n"
	got := ParseBranches(input)
	if got.CurrentBranch != "" || !got.DetachedHead {
		t.Errorf("ParseBranches(detached) = %+v, want detached HEAD", got)
	}
}
