package auth

// LoginUser 跨语言统一身份载体（完整用户信息）。
//
// 字段 json tag 与 Java 侧 LoginUser / X-User-Info 逐字对齐，
// 经 X-User-Info（HTTP 头 / gRPC Metadata）在边界与下游之间零解析透传。
//
// 多租户字段 TenantID 为前瞻预留，本期单租户，不参与鉴权判定。
type LoginUser struct {
	UserID   string   `json:"userId"`
	Roles    []string `json:"roles"`
	Username string   `json:"username"`
	TenantID string   `json:"tenantId"`
	DeviceID string   `json:"deviceId"`
}

// IsAnonymous 匿名：网关白名单接口，下游 UserID 为空即视为匿名。
func (u *LoginUser) IsAnonymous() bool {
	return u == nil || u.UserID == ""
}
