package logx

import (
	"context"
	"testing"

	"github.com/go-kratos/kratos/v2/log"
	"go.opentelemetry.io/otel/trace"
)

// TestTraceID_WithoutSpan 验证 context 中无 span 时 TraceID 取值器返回空串。
func TestTraceID_WithoutSpan(t *testing.T) {
	if got := TraceID()(context.Background()); got != "" {
		t.Fatalf("期望空串, 实际: %v", got)
	}
}

// TestSpanID_WithoutSpan 验证 context 中无 span 时 SpanID 取值器返回空串。
func TestSpanID_WithoutSpan(t *testing.T) {
	if got := SpanID()(context.Background()); got != "" {
		t.Fatalf("期望空串, 实际: %v", got)
	}
}

// TestTraceID_WithSpan 验证 context 中存在 span 时取值器返回正确的 traceId / spanId。
func TestTraceID_WithSpan(t *testing.T) {
	traceID := trace.TraceID{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
		0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10}
	spanID := trace.SpanID{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}
	sc := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    traceID,
		SpanID:     spanID,
		TraceFlags: trace.FlagsSampled,
	})
	ctx := trace.ContextWithSpanContext(context.Background(), sc)

	if got := TraceID()(ctx); got != traceID.String() {
		t.Fatalf("TraceID 期望 %s, 实际: %v", traceID.String(), got)
	}
	if got := SpanID()(ctx); got != spanID.String() {
		t.Fatalf("SpanID 期望 %s, 实际: %v", spanID.String(), got)
	}
}

// TestNewTracedHelper 验证构造的 Helper 非 nil 且业务日志可正常输出。
func TestNewTracedHelper(t *testing.T) {
	helper := NewTracedHelper(log.NewStdLogger(nil), "ai/service")
	if helper == nil {
		t.Fatal("期望返回非 nil 的 Helper")
	}
	// 无 span 场景下取值器安全返回空串，不 panic
	if got := TraceID()(context.Background()); got != "" {
		t.Fatalf("期望空串, 实际: %v", got)
	}
}
