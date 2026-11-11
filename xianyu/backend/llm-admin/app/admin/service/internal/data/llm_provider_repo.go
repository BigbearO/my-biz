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

	llmProviderV1 "github.com/xianyu/llm-admin/api/gen/go/llm_provider/service/v1"
)

type LlmProviderRepo struct {
	gormClient *gormCrud.Client
	log        *slog.Logger

	mapper     *mapper.CopierMapper[llmProviderV1.LlmProvider, models.LlmProvider]
	repository *gormCrud.Repository[llmProviderV1.LlmProvider, models.LlmProvider]
}

func NewLlmProviderRepo(bs *bootstrap.Bootstrap, gormClient *gormCrud.Client) *LlmProviderRepo {
	repo := &LlmProviderRepo{
		gormClient: gormClient,
		log:        bs.Logger.With("module", "llm_provider/repo"),
		mapper:     mapper.NewCopierMapper[llmProviderV1.LlmProvider, models.LlmProvider](),
	}

	repo.init()

	return repo
}

func (r *LlmProviderRepo) init() {
    r.repository = gormCrud.NewRepository[llmProviderV1.LlmProvider, models.LlmProvider](
        r.mapper,
    )

	r.mapper.AppendConverters(copierutil.NewTimeStringConverterPair())
	r.mapper.AppendConverters(copierutil.NewTimeTimestamppbConverterPair())
}

func (r *LlmProviderRepo) List(ctx context.Context, req *pagination.PagingRequest) (*llmProviderV1.ListLlmProviderResponse, error) {
	if req == nil {
		return nil, errors.New("request is nil")
	}

	ret, err := r.repository.ListWithPaging(ctx, r.gormClient.DB, req)
	if err != nil {
		return nil, err
	}
	if ret == nil {
		return &llmProviderV1.ListLlmProviderResponse{Total: 0, Items: nil}, nil
	}

	return &llmProviderV1.ListLlmProviderResponse{
		Total: ret.Total,
		Items: ret.Items,
	}, nil
}

func (r *LlmProviderRepo) Get(ctx context.Context, req *llmProviderV1.GetLlmProviderRequest) (*llmProviderV1.LlmProvider, error) {
	if req == nil {
		return nil, errors.New("request is nil")
	}

	var whereCond *gorm.DB
	switch req.QueryBy.(type) {
	case *llmProviderV1.GetLlmProviderRequest_Id:
		whereCond = r.gormClient.Where("id = ?", req.GetId())
	default:
		whereCond = r.gormClient.Where("id = ?", req.GetId())
	}

	dto, err := r.repository.Get(ctx, whereCond, req.GetViewMask())
	if err != nil {
		return nil, err
	}

	return dto, err
}

func (r *LlmProviderRepo) Create(ctx context.Context, req *llmProviderV1.CreateLlmProviderRequest) (*llmProviderV1.LlmProvider, error) {
	if req == nil || req.Data == nil {
		return nil, errors.New("request is nil")
	}

	result, err := r.repository.Create(ctx, r.gormClient.DB, req.Data, nil)

	return result, err
}

func (r *LlmProviderRepo) Update(ctx context.Context, req *llmProviderV1.UpdateLlmProviderRequest) (*llmProviderV1.LlmProvider, error) {
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

func (r *LlmProviderRepo) Upsert(ctx context.Context, req *llmProviderV1.UpdateLlmProviderRequest) (*llmProviderV1.LlmProvider, error) {
	if req == nil || req.Data == nil {
		return nil, errors.New("request is nil")
	}

	var err error

	result, err := r.repository.Upsert(ctx, r.gormClient.DB, req.Data, req.GetUpdateMask())

	return result, err
}

func (r *LlmProviderRepo) Delete(ctx context.Context, req *llmProviderV1.DeleteLlmProviderRequest) (bool, error) {
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
