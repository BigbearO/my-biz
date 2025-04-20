package main

import (
	"bytedangcedemo/user/initialize"
	user "bytedangcedemo/user/kitex_gen/user/userservice"
	mysql "bytedangcedemo/user/pkg"
	"github.com/cloudwego/kitex/pkg/klog"
	"github.com/cloudwego/kitex/pkg/rpcinfo"
	"github.com/cloudwego/kitex/pkg/utils"
	"github.com/cloudwego/kitex/server"
	"log"
)

func main() {
	startV2()
}

func startV1() {
	svr := user.NewServer(new(UserServiceImpl))

	err := svr.Run()

	if err != nil {
		log.Println(err.Error())
	}
}

func startV2() {
	r, info := initialize.InitRegistry()
	db := initialize.InitDB()

	// Create new server.
	srv := user.NewServer(&UserServiceImpl{
		MySqlManager: mysql.NewUserManager(db),
	},
		server.WithServiceAddr(utils.NewNetAddr("tcp", "10.20.72.188:8881")),
		server.WithRegistry(r),
		server.WithRegistryInfo(info),
		server.WithServerBasicInfo(&rpcinfo.EndpointBasicInfo{ServiceName: "user_srv"}),
	)
	err := srv.Run()
	if err != nil {
		klog.Fatal(err)
	}
}
