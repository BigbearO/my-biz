package manager

import (
	"context"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"

	"llm-manage/app/admin/service/internal/constants"
	"llm-manage/app/admin/service/internal/data"
	"llm-manage/app/admin/service/internal/data/models"
	"llm-manage/app/admin/service/internal/utils"
)

// LlmProviderManager 供应商 manager（基础设施层，原子方法）
type LlmProviderManager struct {
	log         *log.Helper
	providerRepo *data.LlmProviderRepo
	modelRepo    *data.LlmModelRepo
	encryptor    *utils.Encryptor
}

// NewLlmProviderManager 创建供应商 manager
func NewLlmProviderManager(
	ctx *bootstrap.Context,
	providerRepo *data.LlmProviderRepo,
	modelRepo *data.LlmModelRepo,
	encryptor *utils.Encryptor,
) *LlmProviderManager {
	return &LlmProviderManager{
		log:          ctx.NewLoggerHelper(constants.LoggerNameLlmProviderManager),
		providerRepo: providerRepo,
		modelRepo:    modelRepo,
		encryptor:    encryptor,
	}
}

// GetByID 根据 ID 查询供应商（不解密 API Key）
func (m *LlmProviderManager) GetByID(ctx context.Context, id int64) (*models.LlmProvider, error) {
	return m.providerRepo.Get(ctx, id)
}

// GetWithDecryptedKey 根据 ID 查询供应商并解密 API Key
func (m *LlmProviderManager) GetWithDecryptedKey(ctx context.Context, id int64) (*models.LlmProvider, string, error) {
	provider, err := m.providerRepo.Get(ctx, id)
	if err != nil {
		return nil, "", err
	}
	if provider == nil {
		return nil, "", constants.ErrProviderNotFound
	}

	decryptedKey, err := m.encryptor.Decrypt(provider.APIKey)
	if err != nil {
		m.log.Errorf("decrypt api key failed: %v", err)
		return nil, "", constants.ErrDecryptAPIKey
	}

	return provider, decryptedKey, nil
}

// CreateWithEncryptedKey 加密 API Key 后创建供应商
func (m *LlmProviderManager) CreateWithEncryptedKey(
	ctx context.Context,
	name, baseURL, apiKey string,
	status int32,
) (*models.LlmProvider, error) {
	encryptedKey, err := m.encryptor.Encrypt(apiKey)
	if err != nil {
		m.log.Errorf("encrypt api key failed: %v", err)
		return nil, constants.ErrEncryptAPIKey
	}

	provider := &models.LlmProvider{
		Name:    name,
		BaseURL: baseURL,
		APIKey:  encryptedKey,
		Status:  status,
	}
	if provider.Status == constants.StatusDisabled {
		provider.Status = constants.StatusEnabled
	}

	if err := m.providerRepo.Create(ctx, provider); err != nil {
		return nil, err
	}

	return provider, nil
}

// UpdateWithOptionalKey 更新供应商，API Key 为空则不更新
func (m *LlmProviderManager) UpdateWithOptionalKey(
	ctx context.Context,
	id int64,
	name, baseURL, apiKey string,
	status int32,
) (*models.LlmProvider, error) {
	provider, err := m.providerRepo.Get(ctx, id)
	if err != nil {
		return nil, err
	}
	if provider == nil {
		return nil, constants.ErrProviderNotFound
	}

	provider.Name = name
	provider.BaseURL = baseURL
	provider.Status = status

	if apiKey != "" {
		encryptedKey, err := m.encryptor.Encrypt(apiKey)
		if err != nil {
			m.log.Errorf("encrypt api key failed: %v", err)
			return nil, constants.ErrEncryptAPIKey
		}
		provider.APIKey = encryptedKey
	}

	if err := m.providerRepo.Update(ctx, provider); err != nil {
		return nil, err
	}

	return provider, nil
}

// DeleteByID 根据 ID 删除供应商
func (m *LlmProviderManager) DeleteByID(ctx context.Context, id int64) error {
	return m.providerRepo.Delete(ctx, id)
}

// CountModels 统计供应商下的模型数量
func (m *LlmProviderManager) CountModels(ctx context.Context, providerID int64) (int64, error) {
	return m.providerRepo.CountModelsByProviderID(ctx, providerID)
}

// ListWithPage 分页查询供应商列表
func (m *LlmProviderManager) ListWithPage(
	ctx context.Context,
	page, pageSize int32,
	name string,
	status int32,
) ([]*models.LlmProvider, int64, error) {
	return m.providerRepo.List(ctx, page, pageSize, name, status)
}

// MaskAPIKey 脱敏 API Key（先解密再脱敏）
func (m *LlmProviderManager) MaskAPIKey(encryptedKey string) string {
	decryptedKey, err := m.encryptor.Decrypt(encryptedKey)
	if err != nil {
		m.log.Errorf("decrypt api key failed: %v", err)
		return "****"
	}
	return utils.MaskAPIKey(decryptedKey)
}
