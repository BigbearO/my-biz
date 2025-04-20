package initialize

import (
	user "github.com/L2ncE/CloudWeGo-101/kitex_gen/user/userservice"
	"github.com/cloudwego/kitex/client"
	"github.com/cloudwego/kitex/pkg/klog"
	"github.com/cloudwego/kitex/pkg/rpcinfo"
	etcd "github.com/kitex-contrib/registry-etcd"
)

// InitUser to init user service
func InitUser() user.Client {
	// init resolver
	r, err := etcd.NewEtcdResolver([]string{"192.168.50.251:2379"})
	if err != nil {
		klog.Fatalf("new etcd client failed: %s", err.Error())
	}

	// create a new client
	c, err := user.NewClient(
		"user_srv",
		client.WithResolver(r), // service discovery
		client.WithClientBasicInfo(&rpcinfo.EndpointBasicInfo{ServiceName: "user_srv"}),
	)
	if err != nil {
		klog.Fatalf("ERROR: cannot init client: %v\n", err)
	}
	return c
}
