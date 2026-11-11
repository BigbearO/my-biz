package bootstrap

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"log/slog"
	"os"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.34.0"
)

// newTracerProvider 初始化 OTLP 链路 Provider,并注册为全局 Provider/Propagator。
// 返回的 close 在退出时 flush。endpoint 为空时跳过(仅本地开发且无 collector 的场景)。
func newTracerProvider(ctx context.Context, c *Config) (*sdktrace.TracerProvider, func(), error) {
	endpoint := c.Trace.Endpoint
	if endpoint == "" {
		slog.Info("未配置 OTLP 端点,链路追踪未启用")
		return nil, func() {}, nil
	}

	opts := []otlptracegrpc.Option{otlptracegrpc.WithEndpoint(endpoint)}
	if c.Trace.Insecure {
		opts = append(opts, otlptracegrpc.WithInsecure())
	}
	exp, err := otlptracegrpc.New(ctx, opts...)
	if err != nil {
		return nil, nil, fmt.Errorf("创建 OTLP trace exporter 失败: %w", err)
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithSampler(sdktrace.ParentBased(sdktrace.TraceIDRatioBased(c.Trace.sampler()))),
		sdktrace.WithBatcher(exp),
		sdktrace.WithResource(newResource(c)),
	)
	otel.SetTracerProvider(tp)
	otel.SetTextMapPropagator(compositePropagator(&c.Trace))
	return tp, func() { _ = tp.Shutdown(context.Background()) }, nil
}

// newResource 构建 service.* 语义资源。
func newResource(c *Config) *resource.Resource {
	return resource.NewSchemaless(
		semconv.ServiceNamespaceKey.String(c.App.Project),
		semconv.ServiceNameKey.String(c.App.AppID),
		semconv.ServiceVersionKey.String(c.App.Version),
		semconv.ServiceInstanceIDKey.String(instanceID()),
		semconv.DeploymentEnvironmentName(c.App.Env),
	)
}

// compositePropagator 组装传播器:W3C TraceContext(默认开)+ Baggage(默认开)。
func compositePropagator(t *TraceConfig) propagation.TextMapPropagator {
	var parts []propagation.TextMapPropagator
	if t == nil || t.EnableTraceContext {
		parts = append(parts, propagation.TraceContext{})
	}
	if t == nil || t.EnableBaggage {
		parts = append(parts, propagation.Baggage{})
	}
	if len(parts) == 0 {
		return propagation.TraceContext{}
	}
	if len(parts) == 1 {
		return parts[0]
	}
	return propagation.NewCompositeTextMapPropagator(parts...)
}

// instanceID 生成实例标识:host-随机 8 字节,进程生命周期内稳定。
func instanceID() string {
	host, err := os.Hostname()
	if err != nil {
		host = "unknown"
	}
	buf := make([]byte, 4)
	_, _ = rand.Read(buf)
	return fmt.Sprintf("%s-%s", host, hex.EncodeToString(buf))
}
