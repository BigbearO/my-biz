package bootstrap

import (
	"context"
	"log/slog"
	"os"
	"strings"

	otelkratos "github.com/go-kratos/kratos/contrib/otel/v3/log"
	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploggrpc"
	sdklog "go.opentelemetry.io/otel/sdk/log"
	"go.opentelemetry.io/otel/trace"
)

// sensitiveKeys 日志脱敏 key(小写比较)。
var sensitiveKeys = map[string]struct{}{
	"password": {},
	"token":    {},
	"secret":   {},
}

// newLogger 构建双路 slog.Logger:
//   - stdout:JSON,开发期直接可见
//   - OTLP:经 contrib/otel/v3/log(otelslog 桥)转发到 collector,与链路同一端点
//
// 两路均注入 trace_id/span_id(从 ctx 提取)与 service.* 标准字段;敏感 key 统一过滤。
// 返回的 close 负责 flush OTLP 日志。
func newLogger(ctx context.Context, c *Config, endpoint string, insecure bool) (*slog.Logger, func(), error) {
	level := parseLevel(c.Log.Level)

	stdHandler := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level:       level,
		ReplaceAttr: filterSensitiveAttr,
	})

	handlers := []slog.Handler{newTraceHandler(stdHandler)}
	closeFn := func() {}

	if endpoint != "" {
		lp, err := newOTLPLoggerProvider(ctx, endpoint, insecure)
		if err != nil {
			return nil, nil, err
		}
		otelHandler := otelkratos.NewHandler(c.App.AppID, otelkratos.WithLoggerProvider(lp))
		handlers = append(handlers, otelHandler)
		closeFn = func() { _ = lp.Shutdown(context.Background()) }
	} else {
		slog.Warn("未配置 OTLP 端点,日志仅输出到 stdout")
	}

	l := slog.New(newMultiHandler(handlers...)).With(
		"service.id", c.App.AppID,
		"service.name", c.ServiceName(),
		"service.version", c.App.Version,
	)
	return l, closeFn, nil
}

// newOTLPLoggerProvider 创建走 OTLP gRPC 的日志 Provider(批量导出)。
func newOTLPLoggerProvider(ctx context.Context, endpoint string, insecure bool) (*sdklog.LoggerProvider, error) {
	opts := []otlploggrpc.Option{otlploggrpc.WithEndpoint(endpoint)}
	if insecure {
		opts = append(opts, otlploggrpc.WithInsecure())
	}
	exp, err := otlploggrpc.New(ctx, opts...)
	if err != nil {
		return nil, err
	}
	return sdklog.NewLoggerProvider(
		sdklog.WithProcessor(sdklog.NewBatchProcessor(exp)),
	), nil
}

func parseLevel(s string) slog.Level {
	switch strings.ToLower(s) {
	case "debug":
		return slog.LevelDebug
	case "warn", "warning":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}

// filterSensitiveAttr 作为 slog.ReplaceAttr:命中敏感 key 的属性直接删除。
func filterSensitiveAttr(groups []string, a slog.Attr) slog.Attr {
	if len(groups) == 0 && isSensitive(a.Key) {
		return slog.Attr{}
	}
	return a
}

func isSensitive(key string) bool {
	if key == "" {
		return false
	}
	k := strings.ToLower(key)
	if _, ok := sensitiveKeys[k]; ok {
		return true
	}
	// 前缀式命中:如 passwordHash / tokenExpire
	for s := range sensitiveKeys {
		if strings.HasPrefix(k, s) {
			return true
		}
	}
	return false
}

// traceAttrs 从 ctx 提取链路字段,供 stdout 路注入(OTLP 桥自带该能力)。
func traceAttrs(ctx context.Context) []slog.Attr {
	sc := trace.SpanContextFromContext(ctx)
	if !sc.IsValid() {
		return nil
	}
	attrs := make([]slog.Attr, 0, 2)
	if sc.HasTraceID() {
		attrs = append(attrs, slog.String("trace_id", sc.TraceID().String()))
	}
	if sc.HasSpanID() {
		attrs = append(attrs, slog.String("span_id", sc.SpanID().String()))
	}
	return attrs
}
