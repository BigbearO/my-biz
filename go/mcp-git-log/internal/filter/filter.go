// Package filter 提供 commit subject 的噪音过滤：识别 typo / format 类提交。
package filter

import "regexp"

// noisePatterns 匹配 typo / format 类噪音提交的规则（均大小写不敏感）。
var noisePatterns = []*regexp.Regexp{
	// typo 类：subject 中出现 typo / typos（如 "fix typo in README"）
	regexp.MustCompile(`(?i)\btypo(s|es)?\b`),
	// format 类：以 format / fmt / gofmt / clang-format 等开头
	regexp.MustCompile(`(?i)^\s*(format|fmt|gofmt|go fmt|clang-format|prettier)\b`),
	// format 类：subject 中提及格式化工具或 code style
	regexp.MustCompile(`(?i)\b(gofmt|clang-format|prettier|reformat|code style|formatting)\b`),
}

// IsNoise 判断一条 commit subject 是否为 typo/format 类噪音提交。
func IsNoise(subject string) bool {
	for _, re := range noisePatterns {
		if re.MatchString(subject) {
			return true
		}
	}
	return false
}
