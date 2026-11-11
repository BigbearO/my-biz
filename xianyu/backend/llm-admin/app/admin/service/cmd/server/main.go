package main

import (
	"context"
	"fmt"
	"log/slog"
	"os"

	"github.com/xianyu/llm-admin/pkg/bootstrap"
	"github.com/xianyu/llm-admin/pkg/serviceid"
)

// version 由构建注入:go build -ldflags "-X main.version=x.y.z"
var version = "1.0.0"

func main() {
	// -conf 等 flag 由 bootstrap.Run 统一注册并 Parse
	if err := bootstrap.Run(
		context.Background(),
		"./configs",
		newApp,
		bootstrap.WithVersion(version),
	); err != nil {
		slog.Error("服务启动失败", "service", serviceid.AdminService, "err", err)
		_, _ = fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
