package models

import (
	"time"

	"gorm.io/gorm"

	"github.com/xianyu/llm-admin/app/admin/service/internal/pkg/idgen"
)

// LlmProvider 大模型供应商表
//
// 主键为应用侧雪花 ID（autoIncrement:false 必须显式声明，原因见 LlmModel）。
type LlmProvider struct {
	ID        int64          `gorm:"primaryKey;autoIncrement:false;not null;column:id"`
	Name      string         `gorm:"size:64;not null;index;column:name"`
	BaseUrl   string         `gorm:"column:base_url;size:256;not null"`
	ApiKey    string         `gorm:"column:api_key;size:256;not null"`
	Status    int32          `gorm:"not null;default:1;index;column:status"`
	CreatedAt time.Time      `gorm:"autoCreateTime;column:created_at"`
	UpdatedAt time.Time      `gorm:"autoUpdateTime;column:updated_at"`
	DeletedAt gorm.DeletedAt `gorm:"index;column:deleted_at"`
}

// TableName 表名
func (LlmProvider) TableName() string {
	return "llm_provider"
}

// BeforeCreate 插入前填充雪花主键。
func (p *LlmProvider) BeforeCreate(*gorm.DB) error {
	if p.ID != 0 {
		return nil
	}
	id := idgen.Next()
	if id == 0 {
		return idgen.ErrNotInitialized
	}
	p.ID = id
	return nil
}
