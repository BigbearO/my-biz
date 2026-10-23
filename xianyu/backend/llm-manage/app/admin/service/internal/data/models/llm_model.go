package models

import (
	"time"

	"gorm.io/gorm"

	"llm-manage/app/admin/service/internal/pkg/idgen"
)

// LlmModel 大模型表
//
// 主键为应用侧雪花 ID（autoIncrement:false 必须显式声明：GORM 对整型主键
// 在未声明时默认按自增处理，会从 INSERT 中剔除零值 ID，让数据库序列掩盖
// 生成器失效的问题）。
type LlmModel struct {
	ID          int64          `gorm:"primaryKey;autoIncrement:false;not null;column:id"`
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

// BeforeCreate 插入前填充雪花主键（等价 MyBatis-Plus 的 IdType.ASSIGN_ID）。
func (m *LlmModel) BeforeCreate(*gorm.DB) error {
	if m.ID != 0 {
		return nil
	}
	id := idgen.Next()
	if id == 0 {
		return idgen.ErrNotInitialized
	}
	m.ID = id
	return nil
}
