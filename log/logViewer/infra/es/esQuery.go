package es

// 单个 message 条件
type Tag struct {
	Tag      string `form:"tag"`      // 字段名，示例固定为 message
	Op       string `form:"op"`       // match / wildcard / regexp / prefix / term ...
	Value    string `form:"value"`    // 查询值
	Operator string `form:"operator"` // must / should / must_not
}

// 查询参数结构体（支持 level/service/host/trace_id 过滤 + 时间范围 + 分页）
type QueryParam struct {
	Level    string `form:"level"` // debug/info/warn/error
	App      string `form:"app"`
	TraceID  string `form:"traceId"`
	Messages []Tag  `form:"messages"`
	Env      string `form:"env"`
	Start    string `form:"start"` // RFC3339 或 epoch(s)
	End      string `form:"end"`
	Page     int    `form:"page"`      // 从 1 开始
	PageSize int    `form:"page_size"` // 默认 20
}
