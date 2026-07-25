// Package logx 提供基于 OpenTelemetry 的日志增强能力，将链路上下文（trace_id / span_id）
// 以 kratos log.Valuer 的形式注入日志字段，使任意 kratos 服务在日志中自动携带链路追踪信息，
// 便于与 fluent-bit / OpenObserve 等采集端按 trace_id 关联日志与链路。
//
// 设计原则：
//   - 依赖隔离：仅依赖 kratos/log 与 otel/trace，不耦合 kratos-bootstrap，可被任意 kratos 服务复用。
//   - 职责单一：本包只"读取" context 中的 span 信息，不负责 tracer 的初始化（W3C、endpoint 由各服务自行完成）。
//   - 安全降级：context 中不存在有效 span 时返回空串，避免出现无效字段值。
package logx

import (
	"context"

	"github.com/go-kratos/kratos/v2/log"
	"go.opentelemetry.io/otel/trace"
)

// TraceID 返回从 context 提取当前 span 的 traceId 的日志取值器。
// 当 context 中不存在有效 span 时返回空串，避免日志中出现无效值。
//
// 返回值设计为 log.Valuer，配合 log.With 使用，由 kratos 在打日志时惰性求值。
func TraceID() log.Valuer {
	return func(ctx context.Context) interface{} {
		if sc := trace.SpanContextFromContext(ctx); sc.HasTraceID() {
			return sc.TraceID().String()
		}
		return ""
	}
}

// SpanID 返回从 context 提取当前 span 的 spanId 的日志取值器。
// 当 context 中不存在有效 span 时返回空串，避免日志中出现无效值。
//
// 返回值设计为 log.Valuer，配合 log.With 使用，由 kratos 在打日志时惰性求值。
func SpanID() log.Valuer {
	return func(ctx context.Context) interface{} {
		if sc := trace.SpanContextFromContext(ctx); sc.HasSpanID() {
			return sc.SpanID().String()
		}
		return ""
	}
}

// TracedLogger 在基础 logger 上叠加 trace_id / span_id 两个取值器。
// 适用于 logging.Server(TracedLogger(base)) 等中间件日志场景，
// 让框架层日志自动携带链路信息。
//
// 参数 base 为基础日志器（通常来自 bootstrap.Context.GetLogger()）。
func TracedLogger(base log.Logger) log.Logger {
	return log.With(base, "trace_id", TraceID(), "span_id", SpanID())
}

// NewTracedHelper 构造携带模块名与链路信息的日志 Helper，替代 kratos-bootstrap 的 ctx.NewLoggerHelper。
// 在 module 字段基础上再挂载 trace_id / span_id 取值器，返回可直接打日志的 Helper。
//
// 注意：必须配合 helper.WithContext(ctx).Info(...) 使用，取值器才会读取到请求级 ctx 中的 span；
// 若直接调用 Info，将读取构造时传入的 context（通常为 background），取值为空串。
//
// 参数 base 为基础日志器（通常来自 bootstrap.Context.GetLogger()）；
// 参数 module 为业务模块标识，例如 "ai/service"、"ai/repo"。
func NewTracedHelper(base log.Logger, module string) *log.Helper {
	return log.NewHelper(log.With(base,
		"module", module,
		"trace_id", TraceID(),
		"span_id", SpanID(),
	))
}
