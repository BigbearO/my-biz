package initialize

import (
	"github.com/bwmarrin/snowflake"
	"github.com/cloudwego/kitex/pkg/klog"
	"github.com/cloudwego/kitex/pkg/registry"
	"github.com/cloudwego/kitex/pkg/utils"
	etcd "github.com/kitex-contrib/registry-etcd"
	"log"
)

// InitRegistry to init consul
//func InitRegistry() (registry.Registry, *registry.Info) {
//	r, err := consul.NewConsulRegister("127.0.0.1:8500",
//		consul.WithCheck(&api.AgentServiceCheck{
//			Interval:                       "7s",
//			Timeout:                        "5s",
//			DeregisterCriticalServiceAfter: "15s",
//		}))
//	if err != nil {
//		klog.Fatalf("new consul register failed: %s", err.Error())
//	}
//
//	// Using snowflake to generate service name.
//	sf, err := snowflake.NewNode(3)
//	if err != nil {
//		klog.Fatalf("generate service name failed: %s", err.Error())
//	}
//	info := &registry.Info{
//		ServiceName: "article_srv",
//		Addr:        utils.NewNetAddr("tcp", "127.0.0.1:8882"),
//		Tags: map[string]string{
//			"ID": sf.Generate().Base36(),
//		},
//	}
//	return r, info
//}

func InitRegistry() (registry.Registry, *registry.Info) {
	//r, err := consul.NewConsulRegister("127.0.0.1:8500",
	//	consul.WithCheck(&api.AgentServiceCheck{
	//		Interval:                       "7s",
	//		Timeout:                        "5s",
	//		DeregisterCriticalServiceAfter: "15s",
	//	}))

	//r, err := etcd.NewEtcdResolver([]string{"192.168.50.251:2379"}) // r不应重复使用。
	r, err := etcd.NewEtcdRegistry([]string{"192.168.50.251:2379"}) // r不应重复使用。
	if err != nil {
		log.Fatal(err)
	}
	//client, err := userservice.NewClient("userservice",
	//	client.WithResolver(r))
	//if err != nil {
	//	log.Fatal(err)
	//}
	if err != nil {
		klog.Fatalf("new consul register failed: %s", err.Error())
	}

	// Using snowflake to generate service name.
	sf, err := snowflake.NewNode(4)
	if err != nil {
		klog.Fatalf("generate service name failed: %s", err.Error())
	}
	info := &registry.Info{
		ServiceName: "user_srv",
		Addr:        utils.NewNetAddr("tcp", "127.0.0.1:8881"),
		Tags: map[string]string{
			"ID": sf.Generate().Base36(),
		},
	}
	return r, info
}
