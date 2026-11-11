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

func (r *LlmModelRepo) List(ctx context.Context, req *pagination.PagingRequest) (*llmModelV1.ListLlmModelResponse, error) {
	if req == nil {
		return nil, errors.New("request is nil")
	}

	ret, err := r.repository.ListWithPaging(ctx, r.gormClient.DB, req)
	if err != nil {
		return nil, err
	}
	if ret == nil {
		return &llmModelV1.ListLlmModelResponse{Total: 0, Items: nil}, nil
	}

	return &llmModelV1.ListLlmModelResponse{
		Total: ret.Total,
		Items: ret.Items,
	}, nil
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
