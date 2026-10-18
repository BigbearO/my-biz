package constants

import (
	"errors"
	"time"

	kratosErrors "github.com/go-kratos/kratos/v2/errors"
)

// ==================== 状态常量 ====================

const (
	// StatusEnabled 启用
	StatusEnabled = 1
	// StatusDisabled 停用
	StatusDisabled = 0
)

// ==================== 分页默认值 ====================

const (
	// DefaultPage 默认页码
	DefaultPage = 1
	// DefaultPageSize 默认每页数量
	DefaultPageSize = 10
)

// ==================== 模型默认参数 ====================

const (
	// DefaultTemperature 默认温度
	DefaultTemperature = 0.7
	// DefaultMaxTokens 默认最大输出 token 数
	DefaultMaxTokens = 2048
)

// ==================== 日志名称 ====================

var (
	// LoggerNameLlmProviderService 供应商服务日志名
	LoggerNameLlmProviderService = "llm_provider/service/admin-service"
	// LoggerNameLlmModelService 模型服务日志名
	LoggerNameLlmModelService = "llm_model/service/admin-service"
	// LoggerNameLlmProviderRepo 供应商仓储日志名
	LoggerNameLlmProviderRepo = "llm_provider/repo/admin-service"
	// LoggerNameLlmModelRepo 模型仓储日志名
	LoggerNameLlmModelRepo = "llm_model/repo/admin-service"
	// LoggerNameLlmProviderManager 供应商 manager 日志名
	LoggerNameLlmProviderManager = "llm_provider/manager/admin-service"
	// LoggerNameLlmModelManager 模型 manager 日志名
	LoggerNameLlmModelManager = "llm_model/manager/admin-service"
	// LoggerNameEnabledModelCacheManager 启用模型缓存 manager 日志名
	LoggerNameEnabledModelCacheManager = "llm_enabled_model_cache/manager/admin-service"
	// LoggerNameLlmService 模型服务间服务日志名（服务间契约，区别于 admin 侧）
	LoggerNameLlmService = "llm_model/service/grpc-service"
)

// ==================== 错误变量 ====================

var (
	// ErrProviderNotFound 供应商不存在
	ErrProviderNotFound = errors.New("供应商不存在")
	// ErrModelNotFound 模型不存在
	ErrModelNotFound = errors.New("模型不存在")
	// ErrProviderNameEmpty 供应商名称不能为空
	ErrProviderNameEmpty = errors.New("供应商名称不能为空")
	// ErrBaseURLEmpty 接口地址不能为空
	ErrBaseURLEmpty = errors.New("接口地址不能为空")
	// ErrAPIKeyEmpty API Key 不能为空
	ErrAPIKeyEmpty = errors.New("API Key 不能为空")
	// ErrEncryptAPIKey 加密 API Key 失败
	ErrEncryptAPIKey = errors.New("加密 API Key 失败")
	// ErrDecryptAPIKey 解密 API Key 失败
	ErrDecryptAPIKey = errors.New("解密 API Key 失败")
	// ErrProviderHasModels 该供应商下存在模型，无法删除
	ErrProviderHasModels = errors.New("该供应商下存在模型，无法删除")
	// ErrProviderIDRequired 请选择供应商
	ErrProviderIDRequired = errors.New("请选择供应商")
	// ErrModelDisplayNameEmpty 模型显示名称不能为空
	ErrModelDisplayNameEmpty = errors.New("模型显示名称不能为空")
	// ErrModelNameEmpty 实际模型名不能为空
	ErrModelNameEmpty = errors.New("实际模型名不能为空")
	// ErrModelEnabled 启用中的模型无法删除，请先停用
	ErrModelEnabled = errors.New("启用中的模型无法删除，请先停用")
	// ErrEnableModel 启用模型失败
	ErrEnableModel = errors.New("启用模型失败")
	// ErrNoEnabledModel 暂无启用的模型（gRPC NOT_FOUND，对话服务映射 50002）
	ErrNoEnabledModel = kratosErrors.NotFound("NO_ENABLED_MODEL", "暂无启用的模型")
)

// ==================== 启用模型缓存（Redis 提供方缓存） ====================

const (
	// EnabledModelCacheKey 启用模型缓存 key（全局唯一启用模型，单 key）
	EnabledModelCacheKey = "goodfish:llm:enabled_model"
	// EnabledModelCacheTTL 启用模型缓存 TTL（5min 兜底，变更时主动删除）
	EnabledModelCacheTTL = 5 * time.Minute
)

// ==================== 错误消息模板（需要格式化的） ====================

const (
	// MsgProviderHasModelsCount 该供应商下存在 %d 个模型，无法删除
	MsgProviderHasModelsCount = "该供应商下存在 %d 个模型，无法删除"
)

// ==================== 技术常量 ====================

const (
	// HTTPTimeoutSeconds HTTP 请求超时（秒）
	HTTPTimeoutSeconds = 10
	// EncryptKeyLength 加密密钥长度（字节）
	EncryptKeyLength = 32
	// APIKeyMaskPrefix API Key 脱敏前缀长度
	APIKeyMaskPrefix = 4
	// APIKeyMaskSuffix API Key 脱敏后缀长度
	APIKeyMaskSuffix = 4
)

// ==================== 测试连接消息 ====================

const (
	// MsgTestConnectionSuccess 连接成功
	MsgTestConnectionSuccess = "连接成功"
	// MsgTestConnectionSuccessNonStandard 连接成功（响应格式非标准）
	MsgTestConnectionSuccessNonStandard = "连接成功（响应格式非标准）"
	// MsgTestConnectionConfigEmpty 接口地址和 API Key 不能为空
	MsgTestConnectionConfigEmpty = "接口地址和 API Key 不能为空"
	// MsgTestConnectionCreateRequestFailed 创建请求失败
	MsgTestConnectionCreateRequestFailed = "创建请求失败"
)
