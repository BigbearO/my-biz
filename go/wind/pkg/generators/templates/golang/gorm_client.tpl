package client

import (
	"fmt"

	gormCrud "github.com/tx7do/go-crud/gorm"

	"{{.Module}}/app/{{lower .Service}}/service/internal/data/gorm"
	"{{.Module}}/app/{{lower .Service}}/service/internal/data/gorm/dao"

	"{{.Module}}/pkg/bootstrap"
)

// NewGormClient 创建 GORM 数据库客户端(经 go-crud 注入链路追踪与自动迁移)。
func NewGormClient(bs *bootstrap.Bootstrap) (*gormCrud.Client, func(), error) {
	log := bs.Logger.With("module", "data/gorm")

	cfg := bs.Cfg
	if cfg == nil || cfg.Data.Database.Source == "" {
		return nil, func() {}, fmt.Errorf("[GORM] data.database.source 未配置(检查 configs/data.yaml 或 nacos dataId)")
	}

	gorm.RegisterMigrateModels()

	gormClient, err := gormCrud.NewClient(
		gormCrud.WithDriverName(cfg.Data.Database.Driver),
		gormCrud.WithDSN(cfg.Data.Database.Source),
		gormCrud.WithEnableTrace(true),
		gormCrud.WithEnableMigrate(cfg.Data.Database.Migrate),
	)
	if err != nil {
		return nil, func() {}, fmt.Errorf("[GORM] 创建客户端失败: %w", err)
	}
	if gormClient == nil || gormClient.DB == nil {
		return nil, func() {}, fmt.Errorf("[GORM] 创建客户端失败: 实例为空")
	}

	dao.SetDefault(gormClient.DB)

	return gormClient, func() {
		if db, err := gormClient.DB.DB(); err == nil {
			if closeErr := db.Close(); closeErr != nil {
				log.Error("[GORM] 关闭客户端失败", "err", closeErr)
			}
		}
	}, nil
}
