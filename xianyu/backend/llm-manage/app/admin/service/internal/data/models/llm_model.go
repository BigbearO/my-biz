package models

import (
	"time"

	"gorm.io/gorm"
)

// LlmModel 大模型表
type LlmModel struct {
	ID          int64          `gorm:"primaryKey;autoIncrement;column:id"`
	ProviderID  int64          `gorm:"column:provider_id;not null;index"`
	DisplayName string         `gorm:"column:display_name;size:64;not null"`
	ModelName   string         `gorm:"column:model_name;size:64;not null"`
	Temperature float64        `gorm:"not null;default:0.7;column:temperature"`
	MaxTokens   int32          `gorm:"column:max_tokens;not null;default:2048"`
	Status      int32          `gorm:"not null;default:0;index;column:status"`
	CreatedAt   time.Time      `gorm:"autoCreateTime;column:created_at"`
	UpdatedAt   time.Time      `gorm:"autoUpdateTime;column:updated_at"`
	DeletedAt   gorm.DeletedAt `gorm:"index;column:deleted_at"`
}

// TableName 表名
func (LlmModel) TableName() string {
	return "llm_model"
}
