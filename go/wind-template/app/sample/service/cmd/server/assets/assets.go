// Package assets 服务内置静态资源(embed)。
package assets

import _ "embed"

// OpenAPI 由 `buf generate --template buf.gen.openapi.yaml` 生成后自动覆盖;
// 未生成前为最小骨架,仅保证 embed 编译通过。
//
//go:embed openapi.yaml
var OpenAPI []byte
