package generators

import (
	"fmt"
	"regexp"
	"strings"

	"github.com/tx7do/go-utils/stringcase"

	"golang.org/x/text/cases"
	"golang.org/x/text/language"
)

// SnakeToPascal converts a snake_case string to PascalCase.
func SnakeToPascal(snake string) string {
	return stringcase.ToPascalCase(snake)
}

// commonAcronyms lists acronyms that should be kept upper-cased in PascalCase names,
// matching the same list used by entgo.io/ent code generation.
var commonAcronyms = map[string]struct{}{
	"ACL": {}, "API": {}, "ASCII": {}, "AWS": {}, "CPU": {}, "CSS": {}, "DNS": {}, "EOF": {},
	"GB": {}, "GUID": {}, "HCL": {}, "HTML": {}, "HTTP": {}, "HTTPS": {}, "ID": {}, "IP": {},
	"JSON": {}, "KB": {}, "LHS": {}, "MAC": {}, "MB": {}, "QPS": {}, "RAM": {}, "RHS": {},
	"RPC": {}, "SLA": {}, "SMTP": {}, "SQL": {}, "SSH": {}, "SSO": {}, "TCP": {}, "TLS": {},
	"TTL": {}, "UDP": {}, "UI": {}, "UID": {}, "URI": {}, "URL": {}, "UTF8": {}, "UUID": {},
	"VM": {}, "XML": {}, "XMPP": {}, "XSRF": {}, "XSS": {},
}

func SnakeToPascalPlus(snake string) string {
	parts := strings.Split(snake, "_")
	for i := 0; i < len(parts); i++ {
		upper := strings.ToUpper(parts[i])
		if _, ok := commonAcronyms[upper]; ok {
			parts[i] = upper
		} else {
			titleCaser := cases.Title(language.Und)
			parts[i] = titleCaser.String(parts[i])
		}
	}
	return strings.Join(parts, "")
}

func MakeEntSetNillableFunc(fieldName string) string {
	inputVar := "req.Data." + SnakeToPascal(fieldName)
	return "SetNillable" + SnakeToPascalPlus(fieldName) + "(" + inputVar + ")"
}

func MakeEntSetNillableFuncWithTransfer(fieldName string, transFunc string) string {
	inputVar := "req.Data." + SnakeToPascal(fieldName)
	return "SetNillable" + SnakeToPascalPlus(fieldName) + "(" + transFunc + "(" + inputVar + "))"
}

func MakeEntSetFunc(fieldName string) string {
	inputVar := "req.Data.Get" + SnakeToPascal(fieldName) + "()"
	return "Set" + SnakeToPascalPlus(fieldName) + "(" + inputVar + ")"
}

// MakeEntSetFuncWithTransfer NOT NULL + 时间类字段:转换函数返回指针,需解引用为值
// (与 WithStringPtrNumTransfer 对 NOT NULL DECIMAL 的处理一致:DB 非空约束保证值存在)。
func MakeEntSetFuncWithTransfer(fieldName string, transFunc string) string {
	inputVar := "req.Data.Get" + SnakeToPascal(fieldName) + "()"
	return "Set" + SnakeToPascalPlus(fieldName) + "(*" + transFunc + "(" + inputVar + "))"
}

// MakeEntSetFuncWithStringPtrNumTransfer 用于非空 DECIMAL 等字段：
// proto optional 标量的 getter 返回值(string),而转换函数需要指针——
// 直接取字段(req.Data.X 为 *string)后转换并解引用。
func MakeEntSetFuncWithStringPtrNumTransfer(fieldName string, transFunc string) string {
	inputVar := "req.Data." + SnakeToPascal(fieldName)
	return "Set" + SnakeToPascalPlus(fieldName) + "(*" + transFunc + "(" + inputVar + "))"
}

func RemoveTableCommentSuffix(input string) string {
	re := regexp.MustCompile(`(表|table)$`)
	return re.ReplaceAllString(input, "")
}


// ValidateServers 校验 server 类型:v3 谱系仅支持 grpc/rest。
// kratos-transport(kafka/sse/mqtt/mcp 等)冻结在 v2 谱系,未迁移 v3,一律显式报错退出(不做静默降级)。
func ValidateServers(servers []string) error {
	if len(servers) == 0 {
		return nil
	}
	var invalid []string
	for _, s := range servers {
		switch strings.TrimSpace(strings.ToLower(s)) {
		case "grpc", "rest":
		default:
			invalid = append(invalid, s)
		}
	}
	if len(invalid) > 0 {
		return fmt.Errorf("不支持的服务类型: %v;kratos v3 谱系仅支持 grpc/rest(kafka/sse/mqtt/mcp 等传输的依赖 kratos-transport 未升级 v3),请改用 grpc/rest", invalid)
	}
	return nil
}
