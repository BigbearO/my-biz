package main

import (
	"github.com/spf13/cobra"

	"github.com/wind-scaffold/wind/internal/buf"
)

var bufCmd = &cobra.Command{
	Use:   "api",
	Short: "从 proto 生成代码(buf);proto 目录取自 .scaffold.yaml",
	Long: `从 proto 生成代码,等价于对 proto 目录下全部 *.gen.yaml 逐个执行 buf generate。

proto 源目录由项目根 .scaffold.yaml 的 proto_dir 决定(默认 api/protos);
外置公共目录时,buf workspace 跟随 proto_dir,Go 生成物自动写回本项目 api/gen/go。

示例:
  wind api                                          # 全量生成
  wind api --path ../proto/mybiz/service/v1 --lang go
  wind api --path ../proto/mybiz/service/v1/user.proto --lang go,openapi`,
	Args:         cobra.NoArgs,
	RunE:         buf.RunGenerate,
	SilenceUsage: true,
}

func init() {
	bufCmd.Flags().StringArrayVar(&buf.GenPaths, "path", nil, "仅生成指定的 proto 文件/目录(可重复;相对/绝对路径均可,做增量生成)")
	bufCmd.Flags().StringVar(&buf.GenLangs, "lang", "", "按语言过滤生成模板,逗号分隔:go,ts,dart,java,openapi")
	rootCmd.AddCommand(bufCmd)
}
