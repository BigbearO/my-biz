package client

import (
	"fmt"

	"github.com/tx7do/kratos-bootstrap/bootstrap"
	"gorm.io/driver/mysql"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// NewDatabaseClient 创建数据库客户端
func NewDatabaseClient(ctx *bootstrap.Context) (*gorm.DB, func(), error) {
	cfg := ctx.GetConfig()
	if cfg == nil || cfg.Data == nil || cfg.Data.Database == nil {
		return nil, func() {}, nil
	}

	l := ctx.NewLoggerHelper("database/data/admin-service")

	dbCfg := cfg.Data.Database

	var dialector gorm.Dialector
	switch dbCfg.Driver {
	case "postgres", "postgresql":
		dialector = postgres.Open(dbCfg.Source)
	case "mysql":
		dialector = mysql.Open(dbCfg.Source)
	default:
		return nil, func() {}, fmt.Errorf("unsupported database driver: %s", dbCfg.Driver)
	}

	gormConfig := &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	}

	if dbCfg.Debug {
		gormConfig.Logger = logger.Default.LogMode(logger.Info)
	}

	db, err := gorm.Open(dialector, gormConfig)
	if err != nil {
		l.Errorf("failed to connect database: %v", err)
		return nil, func() {}, err
	}

	sqlDB, err := db.DB()
	if err != nil {
		l.Errorf("failed to get sql.DB: %v", err)
		return nil, func() {}, err
	}

	// 连接池配置
	if dbCfg.MaxIdleConnections != nil && *dbCfg.MaxIdleConnections > 0 {
		sqlDB.SetMaxIdleConns(int(*dbCfg.MaxIdleConnections))
	}
	if dbCfg.MaxOpenConnections != nil && *dbCfg.MaxOpenConnections > 0 {
		sqlDB.SetMaxOpenConns(int(*dbCfg.MaxOpenConnections))
	}
	if dbCfg.ConnectionMaxLifetime != nil {
		sqlDB.SetConnMaxLifetime(dbCfg.ConnectionMaxLifetime.AsDuration())
	}

	cleanup := func() {
		if err := sqlDB.Close(); err != nil {
			l.Errorf("failed to close database: %v", err)
		}
	}

	l.Info("database connected successfully")
	return db, cleanup, nil
}
