// Package serviceid 汇集本仓全部服务标识:服务间发现的目标名统一从这里取,避免裸字符串。
package serviceid

// ProjectName 项目命名空间(注册名 = ProjectName/<服务名>)。
const ProjectName = "my-biz"

// 服务注册名清单。
const (
	AdminService = "admin-service"
)
