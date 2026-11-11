package bootstrap

import (
	"errors"
	"os"
	"strings"

	"github.com/go-kratos/kratos/v3/config"
)

// envSource 自实现环境变量源。
// kratos 官方 env source 的键原样保留(大写+下划线),无法命中嵌套配置;
// 这里定义映射规则:WIND_<段>_<段> → 段小写、点分层级,
// 例:WIND_SERVER_HTTP_ADDR → server.http.addr,WIND_TRACE_ENDPOINT → trace.endpoint。
// 注意:驼峰键(dataId/timeoutMs)无法经环境变量表达,这类值请用 local.yaml 或远端配置。
type envSource struct {
	prefix string
}

func newEnvSource(prefix string) config.Source {
	return &envSource{prefix: prefix}
}

func (s *envSource) Load() ([]*config.KeyValue, error) {
	var kvs []*config.KeyValue
	for _, env := range os.Environ() {
		k, v, _ := strings.Cut(env, "=")
		if !strings.HasPrefix(k, s.prefix) || k == s.prefix {
			continue
		}
		rest := strings.TrimPrefix(strings.TrimPrefix(k, s.prefix), "_")
		parts := strings.Split(strings.ToLower(rest), "_")
		for _, p := range parts {
			if p == "" {
				continue
			}
		}
		key := strings.Join(parts, ".")
		if key == "" {
			continue
		}
		kvs = append(kvs, &config.KeyValue{Key: key, Value: []byte(v)})
	}
	return kvs, nil
}

func (s *envSource) Watch() (config.Watcher, error) {
	return &envWatcher{stop: make(chan struct{})}, nil
}

func (s *envSource) String() string { return "env:" + s.prefix }

// envWatcher 环境 变量不支持推送,Next 永久阻塞(kratos Load 要求每个源的 Watch 可用)。
type envWatcher struct{ stop chan struct{} }

func (w *envWatcher) Next() ([]*config.KeyValue, error) {
	<-w.stop
	return nil, errors.New("env watcher stopped")
}

func (w *envWatcher) Stop() error {
	close(w.stop)
	return nil
}
