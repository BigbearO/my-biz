package data

import (
	"context"
	"errors"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"
	"gorm.io/gorm"

	"llm-manage/app/admin/service/internal/constants"
	"llm-manage/app/admin/service/internal/data/models"
)

// LlmProviderRepo 供应商仓储
type LlmProviderRepo struct {
	log *log.Helper
	db  *gorm.DB
}

// NewLlmProviderRepo 创建供应商仓储
func NewLlmProviderRepo(ctx *bootstrap.Context, db *gorm.DB) *LlmProviderRepo {
	return &LlmProviderRepo{
		log: ctx.NewLoggerHelper(constants.LoggerNameLlmProviderRepo),
		db:  db,
	}
}

// List 分页查询供应商列表
func (r *LlmProviderRepo) List(ctx context.Context, page, pageSize int32, name string, status int32) ([]*models.LlmProvider, int64, error) {
	var list []*models.LlmProvider
	var total int64

	query := r.db.WithContext(ctx).Model(&models.LlmProvider{})

	if name != "" {
		query = query.Where("name LIKE ?", "%"+name+"%")
	}
	if status > 0 {
		query = query.Where("status = ?", status)
	}

	if err := query.Count(&total).Error; err != nil {
		r.log.Errorf("count providers failed: %v", err)
		return nil, 0, err
	}

	offset := int((page - 1) * pageSize)
	if err := query.Order("id DESC").Offset(offset).Limit(int(pageSize)).Find(&list).Error; err != nil {
		r.log.Errorf("list providers failed: %v", err)
		return nil, 0, err
	}

	return list, total, nil
}

// Get 获取供应商详情
func (r *LlmProviderRepo) Get(ctx context.Context, id int64) (*models.LlmProvider, error) {
	var provider models.LlmProvider
	if err := r.db.WithContext(ctx).First(&provider, id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		r.log.Errorf("get provider failed: %v", err)
		return nil, err
	}
	return &provider, nil
}

// Create 新增供应商
func (r *LlmProviderRepo) Create(ctx context.Context, provider *models.LlmProvider) error {
	if err := r.db.WithContext(ctx).Create(provider).Error; err != nil {
		r.log.Errorf("create provider failed: %v", err)
		return err
	}
	return nil
}

// Update 更新供应商
func (r *LlmProviderRepo) Update(ctx context.Context, provider *models.LlmProvider) error {
	if err := r.db.WithContext(ctx).Save(provider).Error; err != nil {
		r.log.Errorf("update provider failed: %v", err)
		return err
	}
	return nil
}

// Delete 删除供应商（软删除）
func (r *LlmProviderRepo) Delete(ctx context.Context, id int64) error {
	if err := r.db.WithContext(ctx).Delete(&models.LlmProvider{}, id).Error; err != nil {
		r.log.Errorf("delete provider failed: %v", err)
		return err
	}
	return nil
}

// CountModelsByProviderID 统计供应商下的模型数量
func (r *LlmProviderRepo) CountModelsByProviderID(ctx context.Context, providerID int64) (int64, error) {
	var count int64
	if err := r.db.WithContext(ctx).Model(&models.LlmModel{}).Where("provider_id = ?", providerID).Count(&count).Error; err != nil {
		r.log.Errorf("count models by provider id failed: %v", err)
		return 0, err
	}
	return count, nil
}
