package models

import (
	"time"

	"gorm.io/gorm"
)

// LlmProvider 大模型供应商表
type LlmProvider struct {
	ID        int64          `gorm:"primaryKey;autoIncrement;column:id"`
	Name      string         `gorm:"size:64;not null;index;column:name"`
	BaseURL   string         `gorm:"column:base_url;size:256;not null"`
	APIKey    string         `gorm:"column:api_key;size:256;not null"`
	Status    int32          `gorm:"not null;default:1;index;column:status"`
	CreatedAt time.Time      `gorm:"autoCreateTime;column:created_at"`
	UpdatedAt time.Time      `gorm:"autoUpdateTime;column:updated_at"`
	DeletedAt gorm.DeletedAt `gorm:"index;column:deleted_at"`
}

// TableName 表名
func (LlmProvider) TableName() string {
	return "llm_provider"
}
