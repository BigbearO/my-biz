package data

import (
	"os"

	"gopkg.in/yaml.v3"
)

// seataFileConfig 仅用于读取 seatago.yml 中的 seata.enabled 开关。
//
// seata-go 自身的 InitPath 并不消费 enabled 字段，这里由应用层自行解析：
// 只有显式配置 seata.enabled: true 才开启分布式事务，缺省视为关闭，
// 对标 Java 端 dynamic-datasource 的 seata: true 显式开启语义。
type seataFileConfig struct {
	Seata struct {
		Enabled bool `yaml:"enabled"`
	} `yaml:"seata"`
}

// SeataEnabled 返回分布式事务开关是否开启（默认关闭）。
//
// 关闭时上层应使用普通 pgx 驱动且不初始化 seata 客户端；
// 开启时才调用 client.InitPath 并使用 seata-xa-postgres 驱动。
func SeataEnabled() bool {
	return seataEnabledFrom(resolveSeataConfigPath())
}

// seataEnabledFrom 从指定 seatago.yml 解析 seata.enabled。
//
// 文件缺失或解析失败时一律视为关闭，避免误开启。
//
// 参数 / Parameters:
//   - path: seatago.yml 绝对/相对路径
//
// 返回 / Returns:
//   - bool: 是否开启分布式事务
func seataEnabledFrom(path string) bool {
	data, err := os.ReadFile(path)
	if err != nil {
		return false
	}

	var cfg seataFileConfig
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return false
	}
	return cfg.Seata.Enabled
}
