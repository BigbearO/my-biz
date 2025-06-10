

逻辑就是
1 先读取配置文件，参数配置化,尤其是mq,模拟器的启动目录这些
2 根据配置文件 再判断是否打开模拟器
3 启动一个consumer进行一个个队列监听，根据游戏类型找到对应的class实例化执行对应的run方法--统一封装

上述逻辑代码执行顺序-对应功能入口
main->readyaml ->consumer->通过传递给consumer的handle方法-》handle方法 里面根据游戏类型找到class,再进行实例化
从而执行run方法
 

代码包结构 
业务功能包，assets存放各种截屏图片，etc 参考go-zero 存放配置文件目录，剩下main文件启动入口，app_*文件是业务实现

framework 包
infra--基础设施层
 common下面放 基础公共方法 变量等等
 consumer 消息队列消费模块，暂时只有消费，没有发送
 log 通用日志配置模块,导出后全局使用这个进行日志打印，自动切片拆分
 utils 工具包
task  存放 task 业务功能框架
steps 将任务拆分成一个个的步骤，可选是否执行的



如何开发自己的业务：
 继承baskTask类，覆盖方法 support_game(cls))，run方法，然后参考main.py文件启动


todo 

1 学习adb
adb=ADB()
devices=adb.devices()
d = device()
print(devices)
print(d)

2  之后再进行多线程的处理,现在先实现逻辑





