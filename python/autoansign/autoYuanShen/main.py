import json
import sys
from airtest_mobileauto import Settings
from autoYuanShen.app_yuan_shen import AppYuanShen
from framework.common.property import AutoProperty
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


logInfo = AutoProperty.logInfo

# 入参是json字符串
def run_task(data):
  logInfo.TimeECHO("receive data={}", data)
  info =json.loads(data)
  ce = AppYuanShen(info['account'],info['address'])
  ce.run()

#     1 根据data 实例化 task即app_yuan_shen

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
  print("endpoint=", endpoint)
  print("group=", group)
  # 通过配置文件参数启动，一个topic,一个实力
  consumer = AutoConsumer(run_task, endpoint=endpoint, group="auto-games")
#   consumer.start("games","0")
