package gorm

import (
	gormCurd "github.com/tx7do/go-crud/gorm"

	"github.com/xianyu/llm-admin/app/admin/service/internal/data/gorm/models"
)

func init() {
	RegisterMigrateModels()
}

// RegisterMigrateModels registers all GORM models for migration.
func RegisterMigrateModels() {
	gormCurd.RegisterMigrateModels(
		&models.LlmProvider{},
		&models.LlmModel{},
	)
}
