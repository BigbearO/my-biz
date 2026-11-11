package render

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/tx7do/go-utils/code_generator"
	"github.com/tx7do/go-utils/stringcase"
	"github.com/wind-scaffold/wind/pkg/generators"
)

// WriteGrpcServiceProto write gRPC service proto file
func WriteGrpcServiceProto(outputPath string, data GrpcProtoTemplateData) error {
	outputPath = filepath.Join(outputPath, strings.ToLower(data.Module), "service", strings.ToLower(data.Version))
	outputPath = filepath.Clean(outputPath)

	//log.Printf("Writing gRPC service proto to: [%s] [%s] [%s] [%s]",
	//	outputPath, data.Module, data.Name, data.Comment)

	if err := os.MkdirAll(outputPath, 0o755); err != nil {
		return err
	}

	g := generators.NewProtoGenerator()

	opts := code_generator.Options{
		OutDir: outputPath,
		Vars: map[string]any{
			"Package":   data.Package(),
			"Model":     data.Name,
			"ModelName": data.Comment,
			"Fields":    data.Fields,
		},
	}

	_, err := g.GenerateGrpcServiceProto(context.Background(), opts)

	return err
}

// WriteModelErrorProto 若不存在,为该模型生成最小错误码 proto。
// CRUD 生成的 repo/service 引用 <pkg>.ErrorBadRequest 等助手,由 protoc-gen-go-errors
// 依据这里的 (errors.code) 扩展产出;没有它,生成完的工程不能编译(gow 的已知缺口,此处补齐)。
// 幂等:已存在不覆盖,业务可自行扩充错误码。
func WriteModelErrorProto(outputPath string, module, version, modelName string) error {
	outPath := filepath.Join(outputPath, strings.ToLower(module), "service", strings.ToLower(version))
	outPath = filepath.Clean(outPath)
	if err := os.MkdirAll(outPath, 0o755); err != nil {
		return err
	}

	target := filepath.Join(outPath, stringcase.ToSnakeCase(modelName)+"_error.proto")
	if _, err := os.Stat(target); err == nil {
		return nil
	}

	pkgName := strings.ToLower(module) + ".service." + strings.ToLower(version)
	pascal := stringcase.UpperCamelCase(modelName)
	content := fmt.Sprintf(`syntax = "proto3";

package %s;

import "errors/errors.proto";

enum %sErrorReason {
  option (errors.default_code) = 500;

  // 400
  BAD_REQUEST = 0 [(errors.code) = 400];           // 错误请求
  // 404
  NOT_FOUND = 1 [(errors.code) = 404];             // 资源不存在
  // 409
  CONFLICT = 2 [(errors.code) = 409];              // 冲突
  // 500
  INTERNAL_SERVER_ERROR = 3 [(errors.code) = 500]; // 内部服务器错误
}
`, pkgName, pascal)

	return os.WriteFile(target, []byte(content), 0o644)
}

// WriteRestServiceProto write REST service proto file
func WriteRestServiceProto(outputPath string, data RestProtoTemplateData) error {
	outputPath = filepath.Join(outputPath, strings.ToLower(data.TargetModule), "service", strings.ToLower(data.Version))
	outputPath = filepath.Clean(outputPath)

	if err := os.MkdirAll(outputPath, 0o755); err != nil {
		return err
	}

	g := generators.NewProtoGenerator()

	opts := code_generator.Options{
		OutDir: outputPath,
		Vars: map[string]any{
			"TargetPackage": data.TargetPackage(),
			"SourcePackage": data.SourcePackage(),
			"SourceProto":   data.SourceProto(),
			"ModelName":     data.Comment,
			"Path":          data.Path(),
			"Model":         data.Name,
		},
	}

	_, err := g.GenerateRestServiceProto(context.Background(), opts)

	return err
}
