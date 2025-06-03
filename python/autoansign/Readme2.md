1 暂定使用class里面的类变量 来作为全全局配置项

各种配置分类，每种分类一个class,最终放到里面
然后配置化
//todo  参考go-zero,重写加载配置文件
2
class

暂时值做安卓,和单人类型

逻辑就是
1 先读取配置文件，参数配置化,尤其是mq,模拟器的启动目录这些
2 根据配置文件 再判断是否打开模拟器
3 启动一个consumer进行一个个队列监听，根据游戏类型找到对应的class实例化执行对应的run方法--统一封装

对应功能入口
main->readyaml ->consumer->通过传递给consumer的handle方法-》handle方法 里面根据游戏类型找到class,再进行实例化
从而执行run方法
 

代码结构 
业务功能包，assets存放各种截屏图片，etc 参考go-zero 存放配置文件目录，剩下main文件启动入口，app_*文件是业务实现


framework 包
 common下面放 基础公共方法 变量等等
 consumer 消息队列消费模块，暂时只有消费，没有发送
 log 通用日志配置模块,导出后全局使用这个进行日志打印，自动切片拆分
 entity 存放面向对象基础实体类
 app 存放app操作功能实现
 device 设备操作功能实现

 
类结构
baskTask -> 业务功能实体类-调用AppOB-》 AppOb调用DeviceOB,DqWheel



开发：
 继承baskTask类，覆盖方法 support_game(cls))，run方法，然后参考main.py文件启动






