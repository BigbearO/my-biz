package client

import (
	"context"
	"fmt"

	entCrud "github.com/tx7do/go-crud/entgo"

	_ "github.com/go-sql-driver/mysql"
	_ "github.com/jackc/pgx/v5/stdlib"

	"{{.Module}}/app/{{lower .Service}}/service/internal/data/ent"
	"{{.Module}}/app/{{lower .Service}}/service/internal/data/ent/migrate"
	_ "{{.Module}}/app/{{lower .Service}}/service/internal/data/ent/runtime"

	"{{.Module}}/pkg/bootstrap"
)

// NewEntClient 创建 Ent 数据库客户端(驱动经 otelsql 自动注入链路与指标)。
func NewEntClient(bs *bootstrap.Bootstrap) (*entCrud.EntClient[*ent.Client], func(), error) {
	log := bs.Logger.With("module", "data/ent")

	cfg := bs.Cfg
	if cfg == nil || cfg.Data.Database.Source == "" {
		return nil, func() {}, fmt.Errorf("[ENT] data.database.source 未配置(检查 configs/data.yaml 或 nacos dataId)")
	}

	drv, err := entCrud.CreateDriver(cfg.Data.Database.Driver, cfg.Data.Database.Source, true, true)
	if err != nil {
		return nil, func() {}, fmt.Errorf("[ENT] 创建驱动失败: %w", err)
	}

	client := ent.NewClient(ent.Driver(drv), ent.Log(func(a ...any) {
		log.Debug(fmt.Sprint(a...))
	}))
	if client == nil {
		return nil, func() {}, fmt.Errorf("[ENT] 创建客户端失败")
	}

	// 自动建表(按配置开关;生产环境建议用 wind migrate 的版本化迁移)
	if cfg.Data.Database.Migrate {
		if err := client.Schema.Create(context.Background(), migrate.WithForeignKeys(true)); err != nil {
			_ = client.Close()
			return nil, func() {}, fmt.Errorf("[ENT] schema 迁移失败: %w", err)
		}
	}

	return entCrud.NewEntClient(client, drv), func() {
		if err := client.Close(); err != nil {
			log.Error("[ENT] 关闭客户端失败", "err", err)
		}
	}, nil
}
