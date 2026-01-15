package biz

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/gogf/gf/util/gconv"
	"github.com/nacos-group/nacos-sdk-go/vo"
	"google.golang.org/protobuf/types/known/emptypb"
	v1 "kratosNacosConfig/api/helloworld/v1"
)

const (
	classQuotaGroup  = "class_quota"
	schoolQuotaGroup = "school_quota"
)

func (n *NacosBiz) GetClassQuotaBySchoolNameAndClassName(ctx context.Context, req *v1.SchoolAndClassReq) (*v1.ClassQuotaBase, error) {

	// Notice 学校的名字一定是跟 nacosCliMap 中的key对应上的!
	nacosCli, okCli := n.nacosCliMap[req.GetSchoolName()]
	if !okCli {
		return nil, errors.New(fmt.Sprintf("没有学校 %v 对应的nacosCli,请检查nacosBase中nacosNamespaceMap的配置或确认学校名是否正确!", req.GetSchoolName()))
	}

	classQuotaStr, errCQS := nacosCli.GetConfig(
		vo.ConfigParam{
			DataId: fmt.Sprintf("%v.json", req.GetClassName()),
			Group:  classQuotaGroup,
		},
	)
	if errCQS != nil {
		return nil, errors.New(fmt.Sprintf("从nacos中获取班级定额失败! req: %v, err: %v", gconv.String(req), errCQS))
	}

	// 解析
	ret := v1.ClassQuotaBase{}

	errUnmarshal := json.Unmarshal(gconv.Bytes(classQuotaStr), &ret)
	if errUnmarshal != nil {
		return nil, errUnmarshal
	}

	return &ret, nil
}

func (n *NacosBiz) PublishClassQuotaBySchoolNameAndClassName(ctx context.Context, req *v1.PublishReq) (*emptypb.Empty, error) {

	// 通过 schoolName 找 nacosClient
	// Notice 学校的名字一定是跟 nacosCliMap 中的key对应上的!
	nacosCli, okCli := n.nacosCliMap[req.GetSchoolAndClass().GetSchoolName()]
	if !okCli {
		return nil, errors.New(fmt.Sprintf("没有学校 %v 对应的nacosCli,请检查nacosBase中nacosNamespaceMap的配置或确认学校名是否正确!", req.GetSchoolAndClass().GetSchoolName()))
	}

	classBase := req.GetClassQuota()

	if classBase == nil {
		return nil, errors.New("修改的班级的配置不能为空!")
	}

	dataId := fmt.Sprintf("%v.json", req.GetSchoolAndClass().GetClassName())

	// Notice 注意 修改nacos配置一定得传 "全量" 的数据 ～ 传来的数据字段一定是"全量"的
	// Notice 使用 json.MarshalIndent方法 写入到nacos中的json配置是 "格式化"过的
	// Notice 原生的 json.Marshal方法 写入到nacos的json配置是被压缩过的
	// setBytes, errMarshal := json.Marshal(classBase)
	setBytes, errMarshal := json.MarshalIndent(classBase, "", "\t")
	if errMarshal != nil {
		return nil, errors.New(fmt.Sprintf("errMarshal: %v", errMarshal))
	}

	errSet := n.setNacosClassOrSchoolQuota(dataId, classQuotaGroup, nacosCli, setBytes)
	if errSet != nil {
		return nil, errSet
	}

	return &emptypb.Empty{}, nil
}

func (n *NacosBiz) SearchAllClassQuotasBySchoolName(ctx context.Context, req *v1.SearchAllClassQuotasBySchoolsReq) (*v1.SearchAllClassQuotasBySchoolsReply, error) {

	// 通过 schoolName 找 nacosClient
	// Notice 学校的名字一定是跟 nacosCliMap 中的key对应上的!
	nacosCli, okCli := n.nacosCliMap[req.GetSchoolName()]
	if !okCli {
		return nil, errors.New(fmt.Sprintf("没有学校 %v 对应的nacosCli,请检查nacosBase中nacosNamespaceMap的配置或确认学校名是否正确!", req.GetSchoolName()))
	}

	reply := &v1.SearchAllClassQuotasBySchoolsReply{}
	classQuotas := make([]*v1.ClassQuotaBase, 0)

	// Notice 固定找 class_quota 下所有班级的配置
	// 因为不知道有多少配置 所以建议 "循环获取"
	search := true
	pageNum := 1
	pageSize := 20 // Notice 实际上 如果配置不太多的话 可以把pageSize设置的大一些 保证一次就获取到所有的配置

	for search {
		// Search项得值只能是blur(模糊)或accurate(精确)
		// 查询 固定分组 所有 dataId 的配置 Notice 注意 需要保证 class_quota 这个分组下所有的dataId的配置一样！如果实际上有其他格式的配置 反序列化报错打log即可
		configPage, errSearch := nacosCli.SearchConfig(
			vo.SearchConfigParam{
				Search:   "blur", // 模糊查询
				DataId:   "",
				Group:    classQuotaGroup, // 分组固定
				Tag:      "",
				AppName:  "",
				PageNo:   pageNum,
				PageSize: pageSize,
			},
		)
		if errSearch != nil {
			return nil, errors.New(fmt.Sprintf("errSearch: %v", errSearch))
		}

		fmt.Println("configPage:>>> ", gconv.String(configPage))

		// 跳出循环的条件
		if configPage.PageNumber >= configPage.PagesAvailable {
			search = false
		}

		// 查询下一页
		pageNum++

		if len(configPage.PageItems) > 0 {
			for _, item := range configPage.PageItems {
				// 读取配置
				confJson, errGetConfig := nacosCli.GetConfig(
					vo.ConfigParam{
						DataId: item.DataId,
						Group:  item.Group,
					},
				)
				if errGetConfig != nil {
					// Notice 如果遇到了中文的配置会获取失败 这里出现错误 打log即可
					n.log.WithContext(ctx).Errorf("errGetConfig: %v", errGetConfig)
					continue
					// return nil, errors.New(fmt.Sprintf("errGetConfig: %v", errGetConfig))
				}

				// 解析
				currRetItem := &v1.ClassQuotaBase{}
				errUnmarshal := json.Unmarshal(gconv.Bytes(confJson), currRetItem)
				// Notice 实际上这个group下可能有其他配置 解析失败的话 打个log 就好了
				if errUnmarshal != nil {
					n.log.WithContext(ctx).Errorf("errUnmarshal: ", errUnmarshal)
					continue
				}

				// append
				classQuotas = append(classQuotas, currRetItem)
			}
		}
	}

	reply.ClassQuotas = classQuotas

	return reply, nil
}

func (n *NacosBiz) SearchClassConfigsBySchoolNameAndClassName(ctx context.Context, req *v1.SearchClassConfigsBySchoolNameAndClassNameReq) (*v1.SearchClassConfigsBySchoolNameAndClassNameReply, error) {

	// 通过 schoolName 找 nacosClient
	// Notice 学校的名字一定是跟 nacosCliMap 中的key对应上的!
	nacosCli, okCli := n.nacosCliMap[req.GetSchoolName()]
	if !okCli {
		return nil, errors.New(fmt.Sprintf("没有学校 %v 对应的nacosCli,请检查nacosBase中nacosNamespaceMap的配置或确认学校名是否正确!", req.GetSchoolName()))
	}

	reply := &v1.SearchClassConfigsBySchoolNameAndClassNameReply{}

	// Notice 不同 group 所有同名 dataId 的配置
	// 因为不知道有多少配置 所以建议 "循环获取"
	search := true
	pageNum := 1
	pageSize := 20 // Notice 实际上 如果配置不太多的话 可以把pageSize设置的大一些 保证一次就获取到所有的配置

	for search {
		// Search项得值只能是blur(模糊)或accurate(精确)
		configPage, errSearch := nacosCli.SearchConfig(
			vo.SearchConfigParam{
				Search:   "blur",                                     // 模糊查询
				DataId:   fmt.Sprintf("%v.json", req.GetClassName()), // 同名的 dataId
				Group:    "",
				Tag:      "",
				AppName:  "",
				PageNo:   pageNum,
				PageSize: pageSize,
			},
		)
		if errSearch != nil {
			return nil, errors.New(fmt.Sprintf("errSearch: %v", errSearch))
		}

		fmt.Println("configPage:>>> ", gconv.String(configPage))

		// 跳出循环的条件
		if configPage.PageNumber >= configPage.PagesAvailable {
			search = false
		}

		// 查询下一页
		pageNum++

		if len(configPage.PageItems) > 0 {
			for _, item := range configPage.PageItems {
				// 读取配置
				confJson, errGetConfig := nacosCli.GetConfig(
					vo.ConfigParam{
						DataId: item.DataId,
						Group:  item.Group,
					},
				)
				if errGetConfig != nil {
					// Notice 如果遇到了中文的配置会获取失败 这里出现错误 打log即可
					n.log.WithContext(ctx).Errorf("errGetConfig: %v", errGetConfig)
					continue
				}

				fmt.Println("confJson:>>> ", confJson)
				// 解析
				// Notice 注意: 同名的dataId结构可能不一样！所以需要多次 Unmarshal!
				currQuotaBase := &v1.ClassQuotaBase{}
				currManageBase := &v1.ClassManageBase{}

				// TODO 这个方法有点low...
				// 先看看是不是 class_quota 的配置
				err1 := json.Unmarshal(gconv.Bytes(confJson), currQuotaBase)
				//fmt.Println("err1: ", err1)
				// Notice 这里只是为了演示效果 实际上要根据业务情况去判断是否解析成功了！
				if err1 == nil && currQuotaBase.ClassName != "" && currQuotaBase.Students > 0 {
					reply.ClassQuota = currQuotaBase
				} else {
					// 再看看是不是 class_manage 的配置
					err2 := json.Unmarshal(gconv.Bytes(confJson), currManageBase)
					//fmt.Println("err2: ", err2)
					if err2 == nil && currManageBase.ClassName != "" && currManageBase.Reward != "" {
						reply.ClassManage = currManageBase
					}
				}
			}
		}
	}

	return reply, nil
}

func (n *NacosBiz) DeleteClassQuotaBySchoolNameAndClassName(ctx context.Context, req *v1.DeleteQuotaReq) (*emptypb.Empty, error) {

	// 通过 schoolName 找 nacosClient
	// Notice 学校的名字一定是跟 nacosCliMap 中的key对应上的!
	nacosCli, okCli := n.nacosCliMap[req.GetSchoolName()]
	if !okCli {
		return nil, errors.New(fmt.Sprintf("没有学校 %v 对应的nacosCli,请检查nacosBase中nacosNamespaceMap的配置或确认学校名是否正确!", req.GetSchoolName()))
	}

	// group固定
	_, errDelete := nacosCli.DeleteConfig(
		vo.ConfigParam{
			DataId: fmt.Sprintf("%v.json", req.GetClassName()),
			Group:  classQuotaGroup,
		},
	)
	if errDelete != nil {
		return nil, errors.New(fmt.Sprintf("errDelete: %v", errDelete))
	}

	return &emptypb.Empty{}, nil
}

func (n *NacosBiz) PublishSchoolQuotaBySchoolName(ctx context.Context, req *v1.PublishSchoolQuotaBySchoolNameReq) (*emptypb.Empty, error) {

	// 通过 schoolName 找 nacosClient
	// Notice 学校的名字一定是跟 nacosCliMap 中的key对应上的!
	nacosCli, okCli := n.nacosCliMap[req.GetSchoolName()]
	if !okCli {
		return nil, errors.New(fmt.Sprintf("没有学校 %v 对应的nacosCli,请检查nacosBase中nacosNamespaceMap的配置或确认学校名是否正确!", req.GetSchoolName()))
	}

	dataId := fmt.Sprintf("%v.json", req.GetSchoolName())

	schoolQuotaBase := req.GetSchoolQuota()
	if schoolQuotaBase == nil {
		return nil, errors.New("修改的学校的配置不能为空!")
	}

	// Notice 注意 修改nacos配置一定得传 "全量" 的数据 ～ 传来的数据字段一定是"全量"的
	// Notice 使用 json.MarshalIndent方法 写入到nacos中的json配置是 "格式化"过的
	// Notice 原生的 json.Marshal方法 写入到nacos的json配置是被压缩过的
	// setBytes, errMarshal := json.Marshal(schoolQuotaBase)
	setBytes, errMarshal := json.MarshalIndent(schoolQuotaBase, "", "\t")
	if errMarshal != nil {
		return nil, errors.New(fmt.Sprintf("errMarshal: %v", errMarshal))
	}

	errSet := n.setNacosClassOrSchoolQuota(dataId, schoolQuotaGroup, nacosCli, setBytes)
	if errSet != nil {
		return nil, errSet
	}

	return &emptypb.Empty{}, nil

}

func (n *NacosBiz) GetSchoolQuotaBySchoolName(ctx context.Context, req *v1.SearchAllClassQuotasBySchoolsReq) (*v1.SchoolQuotaBase, error) {

	_, okCli := n.nacosCliMap[req.GetSchoolName()]
	if !okCli {
		return nil, errors.New(fmt.Sprintf("没有学校 %v 对应的nacosCli,请检查nacosBase中nacosNamespaceMap的配置或确认学校名是否正确!", req.GetSchoolName()))
	}

	return n.schoolQuotaConfMap[req.GetSchoolName()], nil
}
