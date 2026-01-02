package types

// QueryParam 查询参数
//type QueryParam struct {
//	Level    string `form:"level,optional" json:"level,optional"`
//	App      string `form:"app,optional" json:"app,optional"`
//	TraceID  string `form:"traceId,optional" json:"traceId,optional"`
//	Messages []Tag  `form:"messages,optional" json:"messages,optional"`
//	Env      string `form:"env,optional" json:"env,optional"`
//	Start    string `form:"start,optional" json:"start,optional"`
//	End      string `form:"end,optional" json:"end,optional"`
//	Page     int    `form:"page,optional" json:"page,optional"`
//	PageSize int    `form:"page_size,optional" json:"page_size,optional"`
//}

type QueryParam struct {
	Level    string `json:"level,optional"`
	App      string `json:"app,optional"`
	TraceID  string `json:"traceId,optional"`
	Messages []Tag  `json:"messages,optional"`
	Env      string `json:"env,optional"`
	Start    string `json:"start,optional"`
	End      string `json:"end,optional"`
	Page     int    `json:"page,optional"`
	PageSize int    `json:"page_size,optional"`
}

// Tag 消息标签
type Tag struct {
	Op    string `json:"op"`
	Value string `json:"value"`
}

// LogDoc 日志文档
type LogDoc struct {
	Timestamp  string `json:"@timestamp"`
	Level      string `json:"level"`
	Message    string `json:"message"`
	App        string `json:"app"`
	TraceID    string `json:"trace_id,omitempty"`
	LoggerName string `json:"logger_name"`
	ThreadName string `json:"thread_name"`
	Env        string `json:"env"`
}

// LogResponse 日志响应
type LogResponse struct {
	Code int    `json:"code"`
	Msg  string `json:"msg"`
	Data Data   `json:"data"`
}

// Data 数据
type Data struct {
	Total int64    `json:"total"`
	Logs  []LogDoc `json:"logs"`
}

// Resp 统一响应格式
type Resp struct {
	Code int         `json:"code"`
	Msg  string      `json:"msg"`
	Data interface{} `json:"data,omitempty"`
}
