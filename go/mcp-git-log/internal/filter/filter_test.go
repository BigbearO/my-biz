package filter

import "testing"

func TestIsNoise(t *testing.T) {
	tests := []struct {
		subject string
		want    bool
	}{
		// typo 类
		{"fix typo in README", true},
		{"Fix typos", true},
		{"docs: fix a typo", true},
		{"typo", true},
		{"Fix TYPO in config", true},

		// format 类
		{"format code", true},
		{"fmt", true},
		{"gofmt all files", true},
		{"go fmt ./...", true},
		{"run prettier on src", true},
		{"clang-format applied", true},
		{"reformat config file", true},
		{"style: formatting cleanup", true},
		{"Format: fix indentation", true},

		// 正常提交
		{"feat: add user login", false},
		{"refactor: extract helper function", false},
		{"fix: null pointer in parser", false},
		{"docs: update README", false},
		{"Merge branch 'dev' into master", false},
		{"feat: add formatted output support", false},
	}
	for _, tt := range tests {
		if got := IsNoise(tt.subject); got != tt.want {
			t.Errorf("IsNoise(%q) = %v, want %v", tt.subject, got, tt.want)
		}
	}
}
