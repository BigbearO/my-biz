package bootstrap

import (
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"

	"github.com/go-kratos/kratos/v3/config"

	nacosv2 "github.com/nacos-group/nacos-sdk-go/v2/clients"
	v2config "github.com/nacos-group/nacos-sdk-go/v2/clients/config_client"
	v2constant "github.com/nacos-group/nacos-sdk-go/v2/common/constant"
	v2vo "github.com/nacos-group/nacos-sdk-go/v2/vo"
)

// newNacosV2ConfigSource 用官方 nacos-sdk-go/v2 客户端实现 kratos config.Source。
// 说明:kratos 官方 contrib/config/nacos/v3 基于 v1 SDK(HTTP 拉取),而 nacos 3.x
// 服务端已移除 v1 HTTP 端点,会静默拿到空配置——故此处直接以 v2 SDK(gRPC)实现。
type nacosV2Source struct {
	cli    v2config.IConfigClient
	group  string
	dataID string
}

func (s *nacosV2Source) Load() ([]*config.KeyValue, error) {
	content, err := s.cli.GetConfig(v2vo.ConfigParam{DataId: s.dataID, Group: s.group})
	if err != nil {
		// dataId 不存在时 SDK 会返回非空 error:按「远端暂无配置」处理(先打印原始错误便于诊断)
		if strings.Contains(err.Error(), "not exist") || strings.Contains(err.Error(), "config not found") {
			fmt.Fprintf(os.Stderr, "[nacos-config] 远端暂无配置 dataId=%s group=%s: %v\n", s.dataID, s.group, err)
			return nil, nil
		}
		return nil, err
	}
	if strings.TrimSpace(content) == "" {
		fmt.Fprintf(os.Stderr, "[nacos-config] 远端配置为空 dataId=%s group=%s\n", s.dataID, s.group)
		return nil, nil
	}
	return []*config.KeyValue{{
		Key:    s.dataID,
		Value:  []byte(content),
		Format: "yaml",
	}}, nil
}

// Watch 基于 v2 SDK 的 ListenConfig 实现热更新(变更内容经 OnChange 推入 channel,Next 阻塞取)。
func (s *nacosV2Source) Watch() (config.Watcher, error) {
	w := &nacosV2Watcher{
		cli:    s.cli,
		group:  s.group,
		dataID: s.dataID,
		events: make(chan string, 4),
		done:   make(chan struct{}),
	}
	param := v2vo.ConfigParam{DataId: s.dataID, Group: s.group}
	param.OnChange = func(namespace, group, dataId, data string) {
		select {
		case w.events <- data:
		case <-w.done:
		}
	}
	if err := s.cli.ListenConfig(param); err != nil {
		return nil, fmt.Errorf("ListenConfig: %w", err)
	}
	return w, nil
}

type nacosV2Watcher struct {
	cli    v2config.IConfigClient
	group  string
	dataID string
	events chan string
	done   chan struct{}
	once   sync.Once
}

// Next 阻塞等待一次远端配置变更。
func (w *nacosV2Watcher) Next() ([]*config.KeyValue, error) {
	select {
	case content := <-w.events:
		if strings.TrimSpace(content) == "" {
			return nil, nil
		}
		return []*config.KeyValue{{
			Key:    w.dataID,
			Value:  []byte(content),
			Format: "yaml",
		}}, nil
	case <-w.done:
		return nil, errors.New("watcher stopped")
	}
}

func (w *nacosV2Watcher) Stop() error {
	w.once.Do(func() { close(w.done) })
	_ = w.cli.CancelListenConfig(v2vo.ConfigParam{DataId: w.dataID, Group: w.group})
	return nil
}

func (s *nacosV2Source) String() string { return "nacos-v2:" + s.dataID }

// newNacosV2ConfigClient 创建 v2 配置客户端(与注册中心同一 SDK/连接参数)。
func newNacosV2ConfigClient(nc *NacosConfig) (v2config.IConfigClient, error) {
	host, port, err := nc.nacosAddr()
	if err != nil {
		return nil, err
	}
	return nacosv2.NewConfigClient(v2vo.NacosClientParam{
		ServerConfigs: []v2constant.ServerConfig{{
			IpAddr:      host,
			Port:        port,
			ContextPath: "/nacos",
			Scheme:      "http",
		}},
		ClientConfig: &v2constant.ClientConfig{
			NamespaceId:         nc.Namespace,
			TimeoutMs:           nc.timeoutMs(),
			Username:            nc.Username,
			Password:            nc.Password,
			LogLevel:            "warn",
			NotLoadCacheAtStart: true,
		},
	})
}
