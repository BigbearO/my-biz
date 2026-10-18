package manager

import (
	"context"
	"encoding/json"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/redis/go-redis/v9"
	"github.com/tx7do/kratos-bootstrap/bootstrap"

	"llm-manage/app/admin/service/internal/constants"
)

// EnabledModelCache 启用模型缓存结构（JSON 序列化存入 Redis）
type EnabledModelCache struct {
	Id           int64   `json:"id"`
	ProviderName string  `json:"provider_name"`
	BaseUrl      string  `json:"base_url"`
	ApiKey       string  `json:"api_key"`
	ModelName    string  `json:"model_name"`
	Temperature  float64 `json:"temperature"`
	MaxTokens    int32   `json:"max_tokens"`
}

// EnabledModelCacheManager 启用模型缓存 manager（基础设施层，原子方法）
//
// 提供方负责缓存：Redis 共享缓存，TTL 5min 兜底 + 启用/编辑/删除时主动删除，
// 调用方（对话服务）无感。Redis 不可用时降级直连数据库（返回 nil 视为未命中）。
type EnabledModelCacheManager struct {
	log   *log.Helper
	redis *redis.Client
}

// NewEnabledModelCacheManager 创建启用模型缓存 manager
func NewEnabledModelCacheManager(ctx *bootstrap.Context, redisCli *redis.Client) *EnabledModelCacheManager {
	return &EnabledModelCacheManager{
		log:   ctx.NewLoggerHelper(constants.LoggerNameEnabledModelCacheManager),
		redis: redisCli,
	}
}

// Get 读取启用模型缓存（未命中 / 不可用返回 nil）
func (m *EnabledModelCacheManager) Get(ctx context.Context) *EnabledModelCache {
	if m.redis == nil {
		return nil
	}
	data, err := m.redis.Get(ctx, constants.EnabledModelCacheKey).Bytes()
	if err != nil {
		// redis.Nil 为正常未命中，其余错误降级直连数据库
		return nil
	}
	var cache EnabledModelCache
	if err := json.Unmarshal(data, &cache); err != nil {
		m.log.Warnf("parse enabled model cache failed: %v", err)
		return nil
	}
	return &cache
}

// Set 写入启用模型缓存（TTL 5min）
func (m *EnabledModelCacheManager) Set(ctx context.Context, cache *EnabledModelCache) {
	if m.redis == nil || cache == nil {
		return
	}
	data, err := json.Marshal(cache)
	if err != nil {
		m.log.Warnf("marshal enabled model cache failed: %v", err)
		return
	}
	if err := m.redis.Set(ctx, constants.EnabledModelCacheKey, data, constants.EnabledModelCacheTTL).Err(); err != nil {
		// 写缓存失败不阻断业务（TTL 兜底 + 下次回源）
		m.log.Warnf("set enabled model cache failed: %v", err)
	}
}

// Delete 删除启用模型缓存（模型/供应商变更后主动失效，多实例删除即全局生效）
func (m *EnabledModelCacheManager) Delete(ctx context.Context) {
	if m.redis == nil {
		return
	}
	if err := m.redis.Del(ctx, constants.EnabledModelCacheKey).Err(); err != nil {
		m.log.Warnf("delete enabled model cache failed: %v", err)
	}
}
