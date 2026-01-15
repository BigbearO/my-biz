package pkg

import (
	"fmt"
	"github.com/nacos-group/nacos-sdk-go/vo"
	"testing"
)

func TestN1(t *testing.T) {

	nacosCli := NewNacos("127.0.0.1", "d743fd46-803d-4145-94cf-ca205266d3dd", 8848)

	ret, err := nacosCli.GetConfig(
		vo.ConfigParam{
			DataId: "class2.json",
			Group:  "class_quota",
		},
	)

	if err != nil {
		fmt.Println("err: ", err)
	} else {
		fmt.Println("ret: ", ret)
	}

}
