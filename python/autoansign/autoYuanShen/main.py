import json
import sys

from airtest.core.android.adb import ADB

from autoYuanShen.app_yuan_shen import AppYuanShen
from framework.common.base_func import get_support_class
from framework.common.property import AutoProperty
from framework.common.yaml_reader import readyaml
from framework.consumer.abs_consumer import AutoConsumer
from framework.entity.baseTask import BaseTask

logInfo = AutoProperty.logInfo

# 入参是json字符串
"""
      1 根据data 实例化 task即app_yuan_shen
      2执行task.run
      3 task.stop
  """
def run_task(receive_data):
  logInfo.TimeECHO("receive data={}", receive_data)
  info = json.loads(receive_data)
  # package_name ="com.miHoYo.GenshinImpact"
  # 测试 暂时只用五子棋游戏来测试
  package_name = "com.yuliang.wuziqigame"
  # 根据gameType实例化对应的类
  support_class= get_support_class(info['game'],BaseTask)
  if support_class is None:
    logInfo.TimeECHO("未支持的游戏类型type="+info['game'])
    return
  app = support_class(package_name, info['account'], info['link'],info['game'])
  app.run()
  # app.stop()
  print("finished")
  adb=ADB()
  devices=adb.devices()
  print(devices)

"""

逻辑就是
1 先读取配置文件，参数配置化,尤其是mq,模拟器的启动目录这些
2 根据配置文件 再判断是否打开模拟器
3 启动一个consumer进行一个个队列监听，根据游戏类型找到对应的
  每个消息有对于的游戏类型进行处理
  mq消费的消息有账号信息和模拟器ip信息--因为需要用户扫码登录好，直接在管理端配置好
再序列化成一个实体对象，这个实体对象有各种行为，run方法执行各种
日志的prefix 刚好弄成日期
"""
if __name__ == "__main__":
  config_file = "etc/config.yaml"
  if len(sys.argv) > 1:
    config_file = str(sys.argv[1])
  # todo 配置文件多层默认是dict的
  config = readyaml(config_file)

  AutoProperty.init_property(config)

  endpoint = config.getstr("endpoint")
  group = config.getstr("group")
  print("endpoint=", endpoint)
  print("group=", group)

  mqdata = config.get("mqdata")
  data = json.dumps(mqdata)
  print("group=", data)
  # 通过配置文件参数启动，一个topic,一个实力
  # consumer = AutoConsumer(run_task, endpoint=endpoint, group="auto-games")
  # consumer.start("games","0")
  run_task(data)

