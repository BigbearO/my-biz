package data

import (
	"context"
	"errors"
	"log/slog"

	"gorm.io/gorm"

	"github.com/tx7do/go-utils/copierutil"
	"github.com/tx7do/go-utils/mapper"

	pagination "github.com/tx7do/go-crud/api/gen/go/pagination/v1"
	gormCrud "github.com/tx7do/go-crud/gorm"

	"github.com/xianyu/llm-admin/pkg/bootstrap"

	"github.com/xianyu/llm-admin/app/admin/service/internal/data/gorm/models"

	llmModelV1 "github.com/xianyu/llm-admin/api/gen/go/llm_model/service/v1"
)

type LlmModelRepo struct {
	gormClient *gormCrud.Client
	log        *slog.Logger

	mapper     *mapper.CopierMapper[llmModelV1.LlmModel, models.LlmModel]
	repository *gormCrud.Repository[llmModelV1.LlmModel, models.LlmModel]
}

func NewLlmModelRepo(bs *bootstrap.Bootstrap, gormClient *gormCrud.Client) *LlmModelRepo {
	repo := &LlmModelRepo{
		gormClient: gormClient,
		log:        bs.Logger.With("module", "llm_model/repo"),
		mapper:     mapper.NewCopierMapper[llmModelV1.LlmModel, models.LlmModel](),
	}

	repo.init()

	return repo
}

func (r *LlmModelRepo) init() {
	r.repository = gormCrud.NewRepository[llmModelV1.LlmModel, models.LlmModel](
		r.mapper,
	)

	r.mapper.AppendConverters(copierutil.NewTimeStringConverterPair())
	r.mapper.AppendConverters(copierutil.NewTimeTimestamppbConverterPair())
}

func (r *LlmModelRepo) List(ctx context.Context, req *llmModelV1.ListLlmModelRequest) (*llmModelV1.ListLlmModelResponse, error) {
	if req == nil {
		return nil, errors.New("request is nil")
	}

	db := r.gormClient.DB
	if req.ProviderId != nil {
		db = db.Where("provider_id = ?", req.GetProviderId())
	}
	if req.DisplayName != nil && req.GetDisplayName() != "" {
		db = db.Where("display_name LIKE ?", "%"+req.GetDisplayName()+"%")
	}
	if req.Status != nil {
		db = db.Where("status = ?", req.GetStatus())
	}

	paging := &pagination.PagingRequest{}
	if req.Page != nil {
		paging.Page = req.Page
	}
	if req.PageSize != nil {
		paging.PageSize = req.PageSize
	}

	ret, err := r.repository.ListWithPaging(ctx, db, paging)
	if err != nil {
		return nil, err
	}
	if ret == nil {
		return &llmModelV1.ListLlmModelResponse{Total: 0, Items: nil}, nil
	}

	resp := &llmModelV1.ListLlmModelResponse{
		Total: ret.Total,
		Items: ret.Items,
	}
	r.fillProviderNames(ctx, resp.Items)
	return resp, nil
}

// Enable 启用模型(事务:先全量停用,再启用当前——全局唯一启用)。
func (r *LlmModelRepo) Enable(ctx context.Context, id int64) (*llmModelV1.LlmModel, error) {
	var model models.LlmModel
	err := r.gormClient.DB.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&models.LlmModel{}).Where("status = ?", 1).Update("status", 0).Error; err != nil {
			return err
		}
		if err := tx.Model(&models.LlmModel{}).Where("id = ?", id).Update("status", 1).Error; err != nil {
			return err
		}
		return tx.First(&model, id).Error
	})
	if err != nil {
		return nil, err
	}

	dto := r.mapper.ToDTO(&model)
	r.fillProviderNames(ctx, []*llmModelV1.LlmModel{dto})
	return dto, nil
}

// GetEnabledWithProvider 获取启用中的模型及其供应商实体
// (模型 DTO 含 provider_name 回填;供应商实体含 base_url/api_key 供内部域返回)。
// 无启用模型时 model/provider 均为 nil。
func (r *LlmModelRepo) GetEnabledWithProvider(ctx context.Context) (*llmModelV1.LlmModel, *models.LlmProvider, error) {
	var model models.LlmModel
	if err := r.gormClient.DB.WithContext(ctx).Where("status = ?", 1).First(&model).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil, nil
		}
		return nil, nil, err
	}

	dto := r.mapper.ToDTO(&model)

	var provider models.LlmProvider
	if model.ProviderID > 0 {
		if err := r.gormClient.DB.WithContext(ctx).First(&provider, model.ProviderID).Error; err != nil {
			return nil, nil, err
		}
		dto.ProviderName = &provider.Name
	}

	return dto, &provider, nil
}

// fillProviderNames 批量回填 DTO 的 provider_name(关联 llm_provider 表)。
func (r *LlmModelRepo) fillProviderNames(ctx context.Context, items []*llmModelV1.LlmModel) {
	if len(items) == 0 {
		return
	}
	ids := make([]int64, 0, len(items))
	seen := make(map[int64]struct{}, len(items))
	for _, it := range items {
		if it == nil || it.GetProviderId() == 0 {
			continue
		}
		if _, ok := seen[it.GetProviderId()]; !ok {
			ids = append(ids, it.GetProviderId())
			seen[it.GetProviderId()] = struct{}{}
		}
	}
	if len(ids) == 0 {
		return
	}

	var providers []models.LlmProvider
	if err := r.gormClient.DB.WithContext(ctx).
		Where("id IN ?", ids).
		Select("id", "name").
		Find(&providers).Error; err != nil || len(providers) == 0 {
		return
	}

	names := make(map[int64]string, len(providers))
	for _, p := range providers {
		names[p.ID] = p.Name
	}
	for _, it := range items {
		if n, ok := names[it.GetProviderId()]; ok && it.GetProviderName() == "" {
			it.ProviderName = &n
		}
	}
}

func (r *LlmModelRepo) Get(ctx context.Context, req *llmModelV1.GetLlmModelRequest) (*llmModelV1.LlmModel, error) {
	if req == nil {
		return nil, errors.New("request is nil")
	}

	var whereCond *gorm.DB
	switch req.QueryBy.(type) {
	case *llmModelV1.GetLlmModelRequest_Id:
		whereCond = r.gormClient.Where("id = ?", req.GetId())
	default:
		whereCond = r.gormClient.Where("id = ?", req.GetId())
	}

	dto, err := r.repository.Get(ctx, whereCond, req.GetViewMask())
	if err != nil {
		return nil, err
	}
	r.fillProviderNames(ctx, []*llmModelV1.LlmModel{dto})

	return dto, err
}

func (r *LlmModelRepo) Create(ctx context.Context, req *llmModelV1.CreateLlmModelRequest) (*llmModelV1.LlmModel, error) {
	if req == nil || req.Data == nil {
		return nil, errors.New("request is nil")
	}

	result, err := r.repository.Create(ctx, r.gormClient.DB, req.Data, nil)

	return result, err
}

func (r *LlmModelRepo) Update(ctx context.Context, req *llmModelV1.UpdateLlmModelRequest) (*llmModelV1.LlmModel, error) {
	if req == nil || req.Data == nil {
		return nil, errors.New("request is nil")
	}

	result, err := r.repository.Update(ctx,
		r.gormClient.Where("id = ?", req.Data.GetId()),
		req.Data,
		req.GetUpdateMask(),
	)

	return result, err
}

func (r *LlmModelRepo) Upsert(ctx context.Context, req *llmModelV1.UpdateLlmModelRequest) (*llmModelV1.LlmModel, error) {
	if req == nil || req.Data == nil {
		return nil, errors.New("request is nil")
	}

	var err error

	result, err := r.repository.Upsert(ctx, r.gormClient.DB, req.Data, req.GetUpdateMask())

	return result, err
}

func (r *LlmModelRepo) Delete(ctx context.Context, req *llmModelV1.DeleteLlmModelRequest) (bool, error) {
	if req == nil {
		return false, errors.New("request is nil")
	}

	result, err := r.repository.Delete(
		ctx,
		r.gormClient.Where("id = ?", req.GetId()),
		true,
	)

	return result > 0, err
}

func (r *LlmModelRepo) Count(ctx context.Context, req *pagination.PagingRequest) (int64, error) {
	return r.repository.Count(ctx, r.gormClient.DB, nil)
}

func (r *LlmModelRepo) BatchCreate(ctx context.Context, req *llmModelV1.BatchCreateLlmModelRequest) (*llmModelV1.BatchCreateLlmModelResponse, error) {
	if req == nil || len(req.GetData()) == 0 {
		return nil, errors.New("request is nil")
	}
	ret, err := r.repository.BatchCreate(ctx, r.gormClient.DB, req.GetData(), nil)
	if err != nil {
		return nil, err
	}
	return &llmModelV1.BatchCreateLlmModelResponse{Data: ret}, nil
}
