package biz

import (
	"errors"
	"fmt"
	"github.com/gogf/gf/util/gconv"
	"github.com/nacos-group/nacos-sdk-go/clients/config_client"
	"github.com/nacos-group/nacos-sdk-go/vo"
)

// Notice 可以封装一个通用的 publish 方法
func (n *NacosBiz) setNacosClassOrSchoolQuota(dataId, group string, nacosClient config_client.IConfigClient, setBytes []byte) error {

	// Notice 有就修改 无就创建
	boolRet, errPublish := nacosClient.PublishConfig(
		vo.ConfigParam{
			DataId:   dataId,
			Group:    group,
			Content:  string(setBytes),
			DatumId:  "",
			Type:     vo.JSON,
			OnChange: nil,
		},
	)

	if errPublish != nil {
		return errors.New(fmt.Sprintf("errPublish: %v", gconv.String(errPublish)))
	}

	if !boolRet {
		return errors.New(fmt.Sprintf("boolRet is false: %v", boolRet))
	}

	return nil
}
