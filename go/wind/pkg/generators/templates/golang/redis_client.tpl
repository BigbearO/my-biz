package client

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/redis/go-redis/v9"

	"{{.Module}}/pkg/bootstrap"
)

// NewRedisClient 创建 Redis 客户端。
func NewRedisClient(bs *bootstrap.Bootstrap) (*redis.Client, func(), error) {
	log := bs.Logger.With("module", "data/redis")

	cfg := bs.Cfg
	if cfg == nil {
		return nil, func() {}, fmt.Errorf("[REDIS] 配置为空")
	}

	cli := redis.NewClient(&redis.Options{
		Addr:     cfg.Data.Redis.Addr,
		Password: cfg.Data.Redis.Password,
		DB:       cfg.Data.Redis.DB,
	})

	if err := cli.Ping(context.Background()).Err(); err != nil {
		_ = cli.Close()
		return nil, func() {}, fmt.Errorf("[REDIS] 连接失败(%s): %w", cfg.Data.Redis.Addr, err)
	}
	log.Info("[REDIS] 已连接", "addr", cfg.Data.Redis.Addr)

	return cli, func() {
		if err := cli.Close(); err != nil {
			log.Error("[REDIS] 关闭客户端失败", "err", err)
		}
	}, nil
}
