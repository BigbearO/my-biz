package manager

import (
	"context"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"

	"llm-manage/app/admin/service/internal/constants"
	"llm-manage/app/admin/service/internal/data"
	"llm-manage/app/admin/service/internal/data/models"
)

// LlmModelManager 模型 manager（基础设施层，原子方法）
type LlmModelManager struct {
	log          *log.Helper
	modelRepo    *data.LlmModelRepo
	providerRepo *data.LlmProviderRepo
}

// NewLlmModelManager 创建模型 manager
func NewLlmModelManager(
	ctx *bootstrap.Context,
	modelRepo *data.LlmModelRepo,
	providerRepo *data.LlmProviderRepo,
) *LlmModelManager {
	return &LlmModelManager{
		log:          ctx.NewLoggerHelper(constants.LoggerNameLlmModelManager),
		modelRepo:    modelRepo,
		providerRepo: providerRepo,
	}
}

// GetByID 根据 ID 查询模型
func (m *LlmModelManager) GetByID(ctx context.Context, id int64) (*models.LlmModel, error) {
	return m.modelRepo.Get(ctx, id)
}

// GetWithProvider 根据 ID 查询模型并关联供应商名称
func (m *LlmModelManager) GetWithProvider(ctx context.Context, id int64) (*models.LlmModel, string, error) {
	model, err := m.modelRepo.Get(ctx, id)
	if err != nil {
		return nil, "", err
	}
	if model == nil {
		return nil, "", constants.ErrModelNotFound
	}

	providerName, err := m.getProviderName(ctx, model.ProviderID)
	if err != nil {
		return nil, "", err
	}

	return model, providerName, nil
}

// ListWithProvider 分页查询模型列表并关联供应商名称
func (m *LlmModelManager) ListWithProvider(
	ctx context.Context,
	page, pageSize int32,
	providerID int64,
	displayName string,
	status int32,
) ([]*models.LlmModel, map[int64]string, int64, error) {
	list, total, err := m.modelRepo.List(ctx, page, pageSize, providerID, displayName, status)
	if err != nil {
		return nil, nil, 0, err
	}

	// 批量查询供应商名称
	providerNameMap, err := m.batchGetProviderNames(ctx, list)
	if err != nil {
		return nil, nil, 0, err
	}

	return list, providerNameMap, total, nil
}

// CreateWithProviderCheck 校验供应商存在后创建模型
func (m *LlmModelManager) CreateWithProviderCheck(
	ctx context.Context,
	providerID int64,
	displayName, modelName string,
	temperature float64,
	maxTokens int32,
	status int32,
) (*models.LlmModel, error) {
	if err := m.checkProviderExists(ctx, providerID); err != nil {
		return nil, err
	}

	model := &models.LlmModel{
		ProviderID:  providerID,
		DisplayName: displayName,
		ModelName:   modelName,
		Temperature: temperature,
		MaxTokens:   maxTokens,
		Status:      status,
	}
	if model.Temperature == 0 {
		model.Temperature = constants.DefaultTemperature
	}
	if model.MaxTokens == 0 {
		model.MaxTokens = constants.DefaultMaxTokens
	}

	if err := m.modelRepo.Create(ctx, model); err != nil {
		return nil, err
	}

	return model, nil
}

// UpdateWithProviderCheck 校验供应商存在后更新模型
func (m *LlmModelManager) UpdateWithProviderCheck(
	ctx context.Context,
	id int64,
	providerID int64,
	displayName, modelName string,
	temperature float64,
	maxTokens int32,
	status int32,
) (*models.LlmModel, error) {
	model, err := m.modelRepo.Get(ctx, id)
	if err != nil {
		return nil, err
	}
	if model == nil {
		return nil, constants.ErrModelNotFound
	}

	if providerID > 0 {
		if err := m.checkProviderExists(ctx, providerID); err != nil {
			return nil, err
		}
		model.ProviderID = providerID
	}

	model.DisplayName = displayName
	model.ModelName = modelName
	model.Temperature = temperature
	model.MaxTokens = maxTokens
	model.Status = status

	if err := m.modelRepo.Update(ctx, model); err != nil {
		return nil, err
	}

	return model, nil
}

// DeleteByID 根据 ID 删除模型
func (m *LlmModelManager) DeleteByID(ctx context.Context, id int64) error {
	return m.modelRepo.Delete(ctx, id)
}

// EnableWithTransaction 事务内启用模型（先全量停用，再启用当前）
func (m *LlmModelManager) EnableWithTransaction(ctx context.Context, id int64) (*models.LlmModel, error) {
	model, err := m.modelRepo.Get(ctx, id)
	if err != nil {
		return nil, err
	}
	if model == nil {
		return nil, constants.ErrModelNotFound
	}

	enabledModel, err := m.modelRepo.Enable(ctx, id)
	if err != nil {
		m.log.Errorf("enable model failed: %v", err)
		return nil, constants.ErrEnableModel
	}

	return enabledModel, nil
}

// GetEnabled 获取启用中的模型
func (m *LlmModelManager) GetEnabled(ctx context.Context) (*models.LlmModel, error) {
	return m.modelRepo.GetEnabled(ctx)
}

// ==================== 内部辅助方法 ====================

// checkProviderExists 校验供应商存在
func (m *LlmModelManager) checkProviderExists(ctx context.Context, providerID int64) error {
	provider, err := m.providerRepo.Get(ctx, providerID)
	if err != nil {
		return err
	}
	if provider == nil {
		return constants.ErrProviderNotFound
	}
	return nil
}

// getProviderName 获取供应商名称
func (m *LlmModelManager) getProviderName(ctx context.Context, providerID int64) (string, error) {
	provider, err := m.providerRepo.Get(ctx, providerID)
	if err != nil {
		return "", err
	}
	if provider != nil {
		return provider.Name, nil
	}
	return "", nil
}

// batchGetProviderNames 批量获取供应商名称
func (m *LlmModelManager) batchGetProviderNames(ctx context.Context, list []*models.LlmModel) (map[int64]string, error) {
	providerNameMap := make(map[int64]string)
	for _, item := range list {
		if _, ok := providerNameMap[item.ProviderID]; !ok {
			name, err := m.getProviderName(ctx, item.ProviderID)
			if err != nil {
				return nil, err
			}
			providerNameMap[item.ProviderID] = name
		}
	}
	return providerNameMap, nil
}
