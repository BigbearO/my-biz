package boot

import (
	kzap "github.com/go-kratos/kratos/contrib/log/zap/v2"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/go-kratos/kratos/v2/middleware/tracing"
	uberZap "go.uber.org/zap"
	"go.uber.org/zap/zapcore"
	"kratosNacosConfig/internal/conf"
	"os"
)

type BootLog struct {
	conf *conf.Bootstrap
}

func (l *BootLog) Run() log.Logger {
	zapLogger := l.Setting()
	logger := log.With(
		kzap.NewLogger(zapLogger),
		"ts", log.Timestamp("2006-01-02 15:04:05.000"),
		"caller", log.DefaultCaller,
		"service.id", l.conf.GetServer().GetId(),
		"service.name", l.conf.GetServer().GetName(),
		"service.version", l.conf.GetServer().GetVersion(),
		"trace.id", tracing.TraceID(),
		"span.id", tracing.SpanID(),
	)

	return logger
}

func (l *BootLog) Setting(opts ...uberZap.Option) *uberZap.Logger {
	var core zapcore.Core
	core = zapcore.NewCore(
		zapcore.NewJSONEncoder(zapcore.EncoderConfig{
			LevelKey:    "level",
			EncodeLevel: zapcore.CapitalLevelEncoder,
		}), // 编码器配置
		zapcore.NewMultiWriteSyncer(
			zapcore.AddSync(os.Stdout),
		),
		uberZap.NewAtomicLevelAt(zapcore.InfoLevel), // Notice 日志级别
	)

	return uberZap.New(core, opts...)
}
