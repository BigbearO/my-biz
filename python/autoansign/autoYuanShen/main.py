import sys
from airtest_mobileauto import Settings
from autoYuanShen.app_yuan_shen import AppYuanShen
from framework.common.yaml_reader import readyaml
from framework.consumer.abs_consumer import AutoConsumer


def test_main():
  config_file = ""
  if len(sys.argv) > 1:
    config_file = str(sys.argv[1])
  Settings.Config(config_file)
  ce = AppYuanShen()
  ce.run()
  exit()


def handle(data):
  print("receive data={}", data)


#     1 根据data 实例化 task

#     执行task.run


"""
逻辑就是
启动一个consumer进行一个个队列监听
每个消息有对于的游戏类型进行处理
消息队列只保护账号信息--提前和用户沟通扫码登录好

再序列化成一个实体对象，这个实体对象有各种行为，run方法执行各种
日志的prefix 刚好弄成日期


"""
if __name__ == "__main__":
  # todo 配置文件多层默认是dict的
  config = readyaml("etc/config.yaml")

  endpoint = config.getstr("endpoint")
  group = config.getstr("group")
  print(endpoint)
  print(group)

# 读取配置文件，获取全局配置
# 实例化endpoint 这些
# consumer=AutoConsumer(handle,endpoint=endpoint,group="auto-games")
# consumer.run("games","0")
