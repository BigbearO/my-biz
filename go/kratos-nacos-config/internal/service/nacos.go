package service

import (
	"context"
	"google.golang.org/protobuf/types/known/emptypb"
	v1 "kratosNacosConfig/api/helloworld/v1"
)

// 精确查找：根据 学校名、班级名获取班级的定额（group固定）
func (s *GreeterService) GetClassQuotaBySchoolNameAndClassName(ctx context.Context, req *v1.SchoolAndClassReq) (*v1.ClassQuotaBase, error) {

	return s.nb.GetClassQuotaBySchoolNameAndClassName(ctx, req)
}

// 修改：根据 学校名、班级名 修改班级定额的配置（group固定）
// 用 PublishConfig 方法：原来文件不存在的话会新建一个对应dataId的文件（演示一下）
func (s *GreeterService) PublishClassQuotaBySchoolNameAndClassName(ctx context.Context, req *v1.PublishReq) (*emptypb.Empty, error) {

	return s.nb.PublishClassQuotaBySchoolNameAndClassName(ctx, req)
}

// 模糊查询1：Search方法:（获取某个namespaceId下固定group的所有DataId的数据）获取某个学校 所有班级定额的配置
// Notice 注意: 一般情况下同一个group下会有不同结构的 dataId 的数据，json解析失败后尽量不要return错误～
func (s *GreeterService) SearchAllClassQuotasBySchoolName(ctx context.Context, req *v1.SearchAllClassQuotasBySchoolsReq) (*v1.SearchAllClassQuotasBySchoolsReply, error) {

	return s.nb.SearchAllClassQuotasBySchoolName(ctx, req)
}

// 模糊查询2: Search方法:（获取某个namespaceId下不同group下所有同名的dataId的数据） 某个学校 相同班级的不同配置(class_quota、class_manage)
func (s *GreeterService) SearchClassConfigsBySchoolNameAndClassName(ctx context.Context, req *v1.SearchClassConfigsBySchoolNameAndClassNameReq) (*v1.SearchClassConfigsBySchoolNameAndClassNameReply, error) {

	return s.nb.SearchClassConfigsBySchoolNameAndClassName(ctx, req)
}

// 删除配置 DeleteConfig（group固定）
func (s *GreeterService) DeleteClassQuotaBySchoolNameAndClassName(ctx context.Context, req *v1.DeleteQuotaReq) (*emptypb.Empty, error) {

	return s.nb.DeleteClassQuotaBySchoolNameAndClassName(ctx, req)
}

// 根据学校名 修改 school_quota的配置（group固定）
func (s *GreeterService) PublishSchoolQuotaBySchoolName(ctx context.Context, req *v1.PublishSchoolQuotaBySchoolNameReq) (*emptypb.Empty, error) {

	return s.nb.PublishSchoolQuotaBySchoolName(ctx, req)
}

// 根据学校名 获取 school_quota的配置（如果QPS太高需要将配置信息加载到本地内存 ———— 监听配置变更修改本地内存的数据即可～）
func (s *GreeterService) GetSchoolQuotaBySchoolName(ctx context.Context, req *v1.SearchAllClassQuotasBySchoolsReq) (*v1.SchoolQuotaBase, error) {

	return s.nb.GetSchoolQuotaBySchoolName(ctx, req)
}
