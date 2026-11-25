package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"

	"github.com/wind-scaffold/wind/internal/scaffold"
)

var protoCmd = &cobra.Command{
	Use:   "proto",
	Short: "新增 proto 模板文件(两种目录模式同内容生成,代码生成交给 wind api)",
	Long: `按模板布局约定新增 proto 文件:<module>/service/<version>/<snake>.proto。

proto 源目录由项目根 .scaffold.yaml 的 proto_dir 决定(默认 api/protos,可指公共目录);
项目内/公共目录两种模式下产出逐字节一致,业务 proto 迁移只改 .scaffold.yaml。

说明:不转调官方 kratos proto add——它的包名/go_package 从完整路径段推导
(会把 api/protos 等无关段拼进包名,go_package 也不带 gen/go 重定向),
与模板约定不一致;代码生成统一走 wind api(buf)。`,
	SilenceUsage: true,
}

var protoAddCmd = &cobra.Command{
	Use:   "add <相对proto_dir的路径>",
	Short: "新增一个 proto API 模板文件",
	Long: `例:wind proto add mybiz/service/v1/user.proto
布局约定:<module>/service/<version>/<snake>.proto(与 gow/官方一致)。`,
	Args:         cobra.ExactArgs(1),
	RunE:         runProtoAdd,
	SilenceUsage: true,
}

func init() {
	protoCmd.AddCommand(protoAddCmd)
	rootCmd.AddCommand(protoCmd)
}

// runProtoAdd 两种模式统一走同一模板(内容/包名/布局逐字节一致,仅落盘位置不同)。
func runProtoAdd(cmd *cobra.Command, args []string) error {
	rel := filepath.ToSlash(filepath.Clean(args[0]))
	if strings.HasPrefix(rel, "..") || filepath.IsAbs(rel) {
		return fmt.Errorf("路径请相对 proto_dir 书写,例:mybiz/service/v1/user.proto")
	}
	if !strings.HasSuffix(rel, ".proto") {
		rel += ".proto"
	}

	cfg, root, err := scaffold.Find()
	if err != nil {
		return err
	}
	protoDir := cfg.AbsProtoDir(root)
	target := filepath.Join(protoDir, filepath.FromSlash(rel))

	if _, err := os.Stat(target); err == nil {
		return fmt.Errorf("文件已存在: %s", target)
	}

	mode := "项目内"
	if cfg.IsExternal(root) {
		mode = "外置公共目录"
	}
	fmt.Printf("→ 生成 proto 模板(%s): %s\n", mode, target)
	return writeProtoTemplate(target, cfg.Module, rel)
}

// writeProtoTemplate 按模板布局约定生成 proto。
// 目录段 = <module>/service/<version>;包名 = 目录段点分(mybiz.service.v1);
// go_package = <module>/api/gen/go/<目录段>;别名 = domain 段+version 段(mybizv1)。
func writeProtoTemplate(target, module, rel string) error {
	rel = strings.TrimSuffix(rel, ".proto")
	segs := strings.Split(rel, "/")
	if len(segs) < 2 {
		return fmt.Errorf("路径需为 <module>/service/<version>/<file>.proto,当前: %s", rel+".proto")
	}
	dirSegs := segs[:len(segs)-1] // 去掉文件名,包名只取目录段
	pkgName := strings.Join(dirSegs, ".")
	dirPath := strings.Join(dirSegs, "/")
	if module == "" {
		module = "REPLACE_ME"
	}
	// 别名约定(与 kratos-template 的 helloworldv1 一致):<domain 段> + <version 段>,跳过字面量 service。
	alias := ""
	switch {
	case len(dirSegs) >= 3:
		alias = dirSegs[0] + dirSegs[len(dirSegs)-1]
	case len(dirSegs) == 2:
		alias = dirSegs[0] + dirSegs[1]
	default:
		alias = dirSegs[0]
	}
	goPkg := strings.TrimSuffix(module, "/") + "/api/gen/go/" + dirPath + ";" + alias

	// 服务/消息名:取文件名 snake → Pascal(与官方 add 的派生方式一致)
	base := segs[len(segs)-1]
	pascal := snakeToPascal(base)

	content := fmt.Sprintf(`syntax = "proto3";

package %s;

import "google/api/annotations.proto";

option go_package = "%s";

service %s {
  rpc Get%s (Get%sRequest) returns (Get%sReply) {
    option (google.api.http) = {
      get: "/%s/{id}"
    };
  }
}

message Get%sRequest {
  string id = 1;
}

message Get%sReply {
  string message = 1;
}
`, pkgName, goPkg, pascal, pascal, pascal, pascal, dirPath, pascal, pascal)

	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		return err
	}
	if err := os.WriteFile(target, []byte(content), 0o644); err != nil {
		return err
	}
	fmt.Printf("✅ proto 已生成: %s\n", target)
	return nil
}

// snakeToPascal user_order → UserOrder。
func snakeToPascal(s string) string {
	parts := strings.Split(s, "_")
	var b strings.Builder
	for _, p := range parts {
		if p == "" {
			continue
		}
		b.WriteString(strings.ToUpper(p[:1]) + p[1:])
	}
	return b.String()
}
