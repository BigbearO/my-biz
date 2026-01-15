# kratos项目使用Nacos作为配置中心的操作介绍

## 参考资料

- [github源码地址](https://github.com/alibaba/nacos)
- [nacos中文文档](https://nacos.io/zh-cn/docs/what-is-nacos.html)
- [Nacos开发教程之后台系统使用](https://juejin.cn/post/7032301402340720671)

## nacos中重要的点

`命名空间namespace`、`group`、`dataId`

**❗️特别注意：group与dataId不可以使用中文命名！否则使用sdk获取配置时会提示找不到配置！！！**

![nacos重要概念.png](img%2Fnacos%E9%87%8D%E8%A6%81%E6%A6%82%E5%BF%B5.png)

### 初始化nacos的客户端指定时指定CacheDir获取到配置后本地会有一个缓存目录

- **看一下命名规范：(dataId)@@(group)@@(namespaceId)**

![nacos_cache.png](img%2Fnacos_cache_new.png)

## 相关技术点

- nacosClient的所有方法都介绍一下
- 初始化nacosClient需要的参数：`addr`、`port`、`namespaceId`
- 项目初始化的时候(在boot/bootConf.go中)
  先根据本地的配置获取连接nacos的基础配置项(`当然这些配置项也可以放在环境变量中获取,效果都一样`)
  ，然后拿着这些配置项再初始化nacos的基础客户端，从nacos中对应的配置文件中获取server/db/redis的配置
- 在`biz/boot/bootConf.go`中加了一个`GetNacosBaseClient方法`
  ,因为nacosBaseClient是在项目初始化的时候读取本地yaml中的配置初始化的，如果业务中需要获取这个namespace中的其他配置，需要用到这个client，在boot中加上一个方法返回这个baseClient，业务中根据这个client去获取相应的`group与dataId`
  中的配置
- 本地配置与nacos配置的yaml解析都放在了`conf/conf.proto`中统一管理了，用的时候记得传入的是什么、从传入的配置中可以获取到什么配置
- 多namespace管理 —— nacosClientMap
- GetConfig: 精确查询
- SearchConfig: 模糊查询
- PublishConfig: 有就修改、无就新增（❗️注意传全量的配置）
- DeleteConfig: 删除配置
- 配置监听: ❗️根据学校名 获取 school_quota的配置（如果QPS太高需要将配置信息加载到本地内存 ———— 监听配置变更修改本地内存的数据即可～）
- PublishAggr：发布聚合类配置信息 —— 项目中用的少，这个方法有时间再研究下❓

## mac-m1 使用docker安装nacos-server

```shell
# 先search下
docker search nacos

NAME                             DESCRIPTION                                     STARS     OFFICIAL   AUTOMATED
nacos/nacos-server               This project contains a Docker image meant t…    435                  [OK]
✅zhusaidong/nacos-server-m1       Nacos Server for Apple MacOS M1                 22
nacos/nacos-mysql                                                                  7

# 安装这个版本的 zhusaidong/nacos-server-m1
docker pull zhusaidong/nacos-server-m1:2.0.3

# docker images 验证下
~ > docker images                                                                                             
REPOSITORY                     TAG        IMAGE ID       CREATED         SIZE
zhusaidong/nacos-server-m1     2.0.3      f1d91cde7e60   2 years ago     1.12GB
mysql                          latest     6ef9eb9d764e   9 months ago    530MB
redis                          latest     667aff0d6b0a   9 months ago    111MB
postgres                       latest     2ce4f03f420b   9 months ago    359MB

# run
docker run -d -p 8848:8848 --env MODE=standalone  --name nacos  zhusaidong/nacos-server-m1:2.0.3

# docker ps 看看后台有没有运行
docker ps                                                                                                     

CONTAINER ID   IMAGE                              COMMAND                  CREATED         STATUS         PORTS                    NAMES
cde07371d1e3   zhusaidong/nacos-server-m1:2.0.3   "bin/docker-startup.…"   3 seconds ago   Up 3 seconds   0.0.0.0:8848->8848/tcp   nacos

```

