package data

import (
	"context"
	"errors"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"
	"gorm.io/gorm"

	"llm-manage/app/admin/service/internal/data/models"
)

// LlmModelRepo 模型仓储
type LlmModelRepo struct {
	log *log.Helper
	db  *gorm.DB
}

// NewLlmModelRepo 创建模型仓储
func NewLlmModelRepo(ctx *bootstrap.Context, db *gorm.DB) *LlmModelRepo {
	return &LlmModelRepo{
		log: ctx.NewLoggerHelper("llm_model/repo/admin-service"),
		db:  db,
	}
}

// List 分页查询模型列表
func (r *LlmModelRepo) List(ctx context.Context, page, pageSize int32, providerID int64, displayName string, status int32) ([]*models.LlmModel, int64, error) {
	var list []*models.LlmModel
	var total int64

	query := r.db.WithContext(ctx).Model(&models.LlmModel{})

	if providerID > 0 {
		query = query.Where("provider_id = ?", providerID)
	}
	if displayName != "" {
		query = query.Where("display_name LIKE ?", "%"+displayName+"%")
	}
	if status > 0 {
		query = query.Where("status = ?", status)
	}

	if err := query.Count(&total).Error; err != nil {
		r.log.Errorf("count models failed: %v", err)
		return nil, 0, err
	}

	offset := int((page - 1) * pageSize)
	if err := query.Order("id DESC").Offset(offset).Limit(int(pageSize)).Find(&list).Error; err != nil {
		r.log.Errorf("list models failed: %v", err)
		return nil, 0, err
	}

	return list, total, nil
}

// Get 获取模型详情
func (r *LlmModelRepo) Get(ctx context.Context, id int64) (*models.LlmModel, error) {
	var model models.LlmModel
	if err := r.db.WithContext(ctx).First(&model, id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		r.log.Errorf("get model failed: %v", err)
		return nil, err
	}
	return &model, nil
}

// Create 新增模型
func (r *LlmModelRepo) Create(ctx context.Context, model *models.LlmModel) error {
	if err := r.db.WithContext(ctx).Create(model).Error; err != nil {
		r.log.Errorf("create model failed: %v", err)
		return err
	}
	return nil
}

// Update 更新模型
func (r *LlmModelRepo) Update(ctx context.Context, model *models.LlmModel) error {
	if err := r.db.WithContext(ctx).Save(model).Error; err != nil {
		r.log.Errorf("update model failed: %v", err)
		return err
	}
	return nil
}

// Delete 删除模型（软删除）
func (r *LlmModelRepo) Delete(ctx context.Context, id int64) error {
	if err := r.db.WithContext(ctx).Delete(&models.LlmModel{}, id).Error; err != nil {
		r.log.Errorf("delete model failed: %v", err)
		return err
	}
	return nil
}

// Enable 启用模型（事务：先全量停用，再启用当前）
func (r *LlmModelRepo) Enable(ctx context.Context, id int64) (*models.LlmModel, error) {
	var model models.LlmModel

	err := r.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		// 先把所有模型设为停用
		if err := tx.Model(&models.LlmModel{}).Where("status = ?", 1).Update("status", 0).Error; err != nil {
			r.log.Errorf("disable all models failed: %v", err)
			return err
		}

		// 再把当前模型设为启用
		if err := tx.Model(&models.LlmModel{}).Where("id = ?", id).Update("status", 1).Error; err != nil {
			r.log.Errorf("enable model failed: %v", err)
			return err
		}

		// 查询当前模型
		if err := tx.First(&model, id).Error; err != nil {
			r.log.Errorf("get enabled model failed: %v", err)
			return err
		}

		return nil
	})

	if err != nil {
		return nil, err
	}

	return &model, nil
}

// GetEnabled 获取启用中的模型
func (r *LlmModelRepo) GetEnabled(ctx context.Context) (*models.LlmModel, error) {
	var model models.LlmModel
	if err := r.db.WithContext(ctx).Where("status = ?", 1).First(&model).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		r.log.Errorf("get enabled model failed: %v", err)
		return nil, err
	}
	return &model, nil
}
