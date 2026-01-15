package biz

import (
	"encoding/json"
	"fmt"
	v1 "kratosNacosConfig/api/helloworld/v1"
	"strings"
	"sync"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/gogf/gf/util/gconv"
	"github.com/nacos-group/nacos-sdk-go/clients/config_client"
	"github.com/nacos-group/nacos-sdk-go/vo"
)

type NacosBiz struct {
	log                *log.Helper
	greeterBiz         *GreeterBiz
	nacosCliMap        map[string]config_client.IConfigClient
	schoolQuotaConfMap map[string]*v1.SchoolQuotaBase
}

func NewNacosBiz(logger log.Logger, greeterBiz *GreeterBiz, nacosCliMap map[string]config_client.IConfigClient) *NacosBiz {

	nacosBizRet := &NacosBiz{
		log:         log.NewHelper(logger),
		greeterBiz:  greeterBiz,
		nacosCliMap: nacosCliMap,
	}

	// 初始化一下map
	nacosBizRet.schoolQuotaConfMap = make(map[string]*v1.SchoolQuotaBase)
	// 初始化学校定额配置到本地内存 这样就不用一直从nacos中获取了 减少与nacos的IO
	//nacosBizRet.initSchoolQuotaConfMap()
	//
	//// Notice 监听各个namespace下学校定额的配置变更 更新 n.schoolQuotaConfMap
	//nacosBizRet.listenSchoolQuotaConfMap()

	return nacosBizRet
}

func (n *NacosBiz) initSchoolQuotaConfMap() {

	// 从不同 namespace 中获取 school_quota这个group中的 学校定额配置
	for schoolName := range n.nacosCliMap {
		confJson, errGet := n.nacosCliMap[schoolName].GetConfig(
			vo.ConfigParam{
				DataId: fmt.Sprintf("%v.json", schoolName),
				Group:  schoolQuotaGroup,
			},
		)
		if errGet != nil {
			panic(errGet)
		}
		currRet := &v1.SchoolQuotaBase{}
		errUnmarshal := json.Unmarshal(gconv.Bytes(confJson), currRet)
		if errUnmarshal != nil {
			panic(errUnmarshal)
		}
		n.schoolQuotaConfMap[schoolName] = currRet
	}
}

func (n *NacosBiz) listenSchoolQuotaConfMap() {

	for schoolName := range n.nacosCliMap {
		currClient := n.nacosCliMap[schoolName]

		errListen := currClient.ListenConfig(
			vo.ConfigParam{
				DataId:   fmt.Sprintf("%v.json", schoolName),
				Group:    schoolQuotaGroup,
				OnChange: n.refreshSchoolQuotaConfMap,
			},
		)
		if errListen != nil {
			panic(errListen)
		}

	}
}

func (n *NacosBiz) refreshSchoolQuotaConfMap(namespace, group, dataId, data string) {
	fmt.Printf("%v-%v-%v下的学校定额配置更新了！！！\n", namespace, group, dataId)

	// dataId固定: (学校名).json
	schoolName := strings.Split(dataId, ".")[0]
	if schoolName == "" {
		n.log.Errorf("监听配置获取学校名出错了! dataId: %v", dataId)
		return
	}

	currSchoolQuota := &v1.SchoolQuotaBase{}
	errUnmarshal := json.Unmarshal(gconv.Bytes(data), currSchoolQuota)
	if errUnmarshal != nil {
		n.log.Errorf("反序列化配置出错了!! data: %v, err: %v", data, errUnmarshal)
		return
	}

	// Notice 更新map 防止并发出现panic最好加一个锁～
	lock := sync.Mutex{}
	lock.Lock()
	defer lock.Unlock()

	n.schoolQuotaConfMap[schoolName] = currSchoolQuota
}
