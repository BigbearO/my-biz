package es

// 单个 message 条件
type Tag struct {
	Op    string `form:"op" json:"op"`       // 逻辑运算符：and/or/not
	Value string `form:"value" json:"value"` // 查询值，用于ES文字匹配
}

// 查询参数结构体（支持 level/service/host/trace_id 过滤 + 时间范围 + 分页）
type QueryParam struct {
	Level    string `form:"level" json:"level"` // debug/info/warn/error
	App      string `form:"app" json:"app"`
	TraceID  string `form:"traceId" json:"traceId"`
	Messages []Tag  `form:"messages" json:"messages"`
	Env      string `form:"env" json:"env"`
	Start    string `form:"start" json:"start"` // RFC3339 或 epoch(s)
	End      string `form:"end" json:"end"`
	Page     int    `form:"page" json:"page"`          // 从 1 开始
	PageSize int    `form:"page_size" json:"pageSize"` // 默认 20
}
