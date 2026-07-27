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
	"fmt"
	"time"

	"github.com/go-kratos/kratos/v2/errors"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/go-kratos/kratos/v2/middleware"
	"github.com/go-kratos/kratos/v2/transport"
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

// TeeLogger 创建一个双写日志器，同时输出到 fluent（base）和 console（stdout）。
// 解决 kratos-bootstrap fluent logger 只发到 fluent-bit、控制台无输出的问题。
//
// 参数 base 为原始日志器（通常来自 bootstrap.Context.GetLogger()）；
// 返回的 Logger 在每次 Log 时同时调用 base 和 stdLogger。
func TeeLogger(base log.Logger) log.Logger {
	std := log.NewStdLogger(nil)
	return &teeLogger{primary: base, secondary: std}
}

type teeLogger struct {
	primary   log.Logger
	secondary log.Logger
}

func (t *teeLogger) Log(level log.Level, keyvals ...interface{}) error {
	err1 := t.primary.Log(level, keyvals...)
	err2 := t.secondary.Log(level, keyvals...)
	if err1 != nil {
		return err1
	}
	return err2
}

// TracedLoggingServer 创建一个带链路上下文传播的服务端日志中间件，
// 替代 kratos 内置的 logging.Server，确保 trace_id / span_id Valuer
// 能从请求 context 中正确提取值。
//
// 与内置 logging.Server 的区别：使用 log.WithContext(ctx, logger) 创建 Helper，
// 使 Valuer 在求值时能读取到请求级 ctx 中的 span 信息。
func TracedLoggingServer(logger log.Logger) middleware.Middleware {
	return func(handler middleware.Handler) middleware.Handler {
		return func(ctx context.Context, req interface{}) (reply interface{}, err error) {
			var (
				code      int32
				reason    string
				kind      string
				operation string
			)
			start := time.Now()
			if info, ok := transport.FromServerContext(ctx); ok {
				kind = info.Kind().String()
				operation = info.Operation()
			}
			reply, err = handler(ctx, req)
			if se := errors.FromError(err); se != nil {
				code = se.Code
				reason = se.Reason
			}
			level := log.LevelInfo
			stack := ""
			if err != nil {
				level = log.LevelError
				stack = fmt.Sprintf("%+v", err)
			}
			log.NewHelper(log.WithContext(ctx, logger)).Log(level,
				"kind", "server",
				"component", kind,
				"operation", operation,
				"args", extractArgs(req),
				"code", code,
				"reason", reason,
				"stack", stack,
				"latency", time.Since(start).Seconds(),
			)
			return
		}
	}
}

// extractArgs 返回请求参数的字符串表示。
func extractArgs(req interface{}) string {
	if stringer, ok := req.(fmt.Stringer); ok {
		return stringer.String()
	}
	return fmt.Sprintf("%+v", req)
}
