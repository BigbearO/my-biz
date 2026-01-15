package client

import (
	"fmt"
	"github.com/nacos-group/nacos-sdk-go/clients/config_client"
	"kratosNacosConfig/internal/conf"
	"kratosNacosConfig/pkg"
)

// nacos client map
func NewNacosClientMap(localYaml *conf.Bootstrap, nacosYamlConf *conf.Bootstrap) map[string]config_client.IConfigClient {

	nacosAddr := localYaml.GetNacos().GetAddr()
	nacosPort := localYaml.GetNacos().GetPort()
	fmt.Println("nacosAddr>>>: ", nacosAddr, "nacosPort>>>: ", nacosPort)

	ret := make(map[string]config_client.IConfigClient, 0)
	if nacosYamlConf.GetNacosNamespaceMap() != nil && len(nacosYamlConf.GetNacosNamespaceMap()) > 0 {
		for key, nameSpaceId := range nacosYamlConf.GetNacosNamespaceMap() {
			fmt.Println("key: ", key, "namespaceId: ", nameSpaceId)
			currCli := pkg.NewNacos(
				nacosAddr, // addr用的是本地配置
				nameSpaceId,
				nacosPort, // port用的是本地配置
			)
			ret[key] = currCli
		}
	}

	return ret
}
