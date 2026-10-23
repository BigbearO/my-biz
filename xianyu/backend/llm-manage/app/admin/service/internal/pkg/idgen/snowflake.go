// Package idgen 提供全局唯一的雪花 ID 生成器。
//
// 三端（llm-manage / goodfish-item-service / goodfish-ai-service）统一约定：
//   - 时间起点 Epoch = 1288834974657（与 MyBatis-Plus Sequence.twepoch、
//     Hutool Snowflake.twepoch 一致）；
//   - 布局：1 符号位 + 41 位时间戳 + 5 位数据中心 + 5 位工作节点 + 12 位序列；
//   - nodeId 取值 0..1023，按服务分段分配：item 1-31 / llm 32-63 / ai 64-95。
//
// 生成器在应用侧生成主键后显式写入 INSERT，数据库列不再自增：
// 关闭自增后若本包未初始化，Next 返回 0，模型 BeforeCreate 会返回错误，
// 而不是让数据库序列悄悄生成一个 ID 掩盖问题。
package idgen

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"sync"

	"github.com/bwmarrin/snowflake"
)

const (
	// Epoch 与 MyBatis-Plus / Hutool 的雪花时间起点保持一致，禁止更改。
	Epoch int64 = 1288834974657

	// NodeIDEnv 通过环境变量注入节点号（K8s 用 StatefulSet 序号或 downward API）。
	NodeIDEnv = "SNOWFLAKE_NODE_ID"

	// DefaultNodeID llm-manage 段的起点（32-63）。
	DefaultNodeID int64 = 32

	// MaxNodeID 节点号为 10 位。
	MaxNodeID int64 = 1023
)

// ErrNotInitialized 生成器未初始化时由模型回调返回，避免插入 id=0。
var ErrNotInitialized = errors.New("snowflake id generator is not initialized")

var (
	mu     sync.RWMutex
	node   *snowflake.Node
	nodeID int64
)

// Init 初始化生成器（幂等，可重复调用以支持测试）。
func Init(id int64) error {
	if id < 0 || id > MaxNodeID {
		return fmt.Errorf("invalid snowflake node id %d, must be within 0-%d", id, MaxNodeID)
	}
	// bwmarrin/snowflake 默认 Epoch 与 MP 相同，这里显式赋值表达意图，
	// 防止依赖升级改动默认值后三端 ID 语义漂移。
	snowflake.Epoch = Epoch
	n, err := snowflake.NewNode(id)
	if err != nil {
		return fmt.Errorf("create snowflake node %d: %w", id, err)
	}
	mu.Lock()
	node = n
	nodeID = id
	mu.Unlock()
	return nil
}

// InitFromEnv 从 SNOWFLAKE_NODE_ID 读取节点号并初始化，缺省使用 llm 段起点。
func InitFromEnv() (int64, error) {
	id := DefaultNodeID
	if raw := os.Getenv(NodeIDEnv); raw != "" {
		parsed, err := strconv.ParseInt(raw, 10, 64)
		if err != nil {
			return 0, fmt.Errorf("parse %s=%q: %w", NodeIDEnv, raw, err)
		}
		id = parsed
	}
	if err := Init(id); err != nil {
		return 0, err
	}
	return id, nil
}

// Next 返回下一个雪花 ID；未初始化时返回 0（由模型回调转成错误）。
func Next() int64 {
	mu.RLock()
	current := node
	mu.RUnlock()
	if current == nil {
		return 0
	}
	return int64(current.Generate())
}

// CurrentNodeID 返回当前节点号，用于启动日志与健康检查。
func CurrentNodeID() int64 {
	mu.RLock()
	defer mu.RUnlock()
	return nodeID
}
