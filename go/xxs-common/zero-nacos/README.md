 

  怎么使用

1 引入 当前包
然后在项目下执行 go get github.com/zeromicro/zero-contrib/zrpc/registry/nacos@latest
保证go。sum有 这个包





2 rpc
配置文件参考 etc-rpc

```

var bootstrapConfigFile = flag.String("f", "app/user/cmd/rpc/etc/bootstrap.yaml", "the config.go file")

func main() {
	//解析bootstrap并获取业务配置
	flag.Parse()
	var bootstrapConfig c2.BootstrapConfig
	var c config.Config

	c2.GetConfig(bootstrapConfigFile, &bootstrapConfig, &c)

	// 进行业务配置
	ctx := svc.NewServiceContext(c)

	s := zrpc.MustNewServer(c.RpcServerConf, func(grpcServer *grpc.Server) {
		pb.RegisterUserRpcServer(grpcServer, server.NewUserRpcServer(ctx))

		if c.Mode == service.DevMode || c.Mode == service.TestMode {
			reflection.Register(grpcServer)
		}
	})
	defer s.Stop()

	// 注册nacos
	register.Register(c)

	s.Start()
}

```





3 api
配置文件参考 etc


```

var bootstrapConfigFile = flag.String("f", "app/user/cmd/api/etc/bootstrap.yaml", "the config.go file")

func main() {
	//解析bootstrap config.go
	flag.Parse()
	var bootstrapConfig c2.BootstrapConfig
	var c config.Config
	c2.GetConfig(bootstrapConfigFile, &bootstrapConfig, &c)

	// 配置业务
	server := rest.MustNewServer(c.RestConf)
	defer server.Stop()

	ctx := svc.NewServiceContext(c)
	handler.RegisterHandlers(server, ctx)

	fmt.Printf("Starting server at %s:%d...\n", c.Host, c.Port)
	server.Start()
}
```



```
svc.NewServiceContext(c) 如下

func NewServiceContext(c config.Config) *ServiceContext {
	return &ServiceContext{
		Config:        c,
		UserRpcClient: userrpc.NewUserRpc(zrpc.MustNewClient(c.UserRpcConfig)),
	}
}

```

