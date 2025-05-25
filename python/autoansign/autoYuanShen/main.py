import json
import sys

from autoYuanShen.app_yuan_shen import AppYuanShen
from framework.common.property import AutoProperty
from framework.common.yaml_reader import readyaml
from framework.consumer.abs_consumer import AutoConsumer
from framework.entity.task import BaseTask

logInfo = AutoProperty.logInfo

# 入参是json字符串
"""
      1 根据data 实例化 task即app_yuan_shen
      2执行task.run
      3 task.stop
  """
def run_task(data):
  logInfo.TimeECHO("receive data={}", data)
  info = json.loads(data)
  # package_name ="com.miHoYo.GenshinImpact"
  # 测试 暂时只用五子棋游戏来测试
  package_name = "com.yuliang.wuziqigame"
  # 根据gameType实例化对应的类
  support_class= get_support_class(info['game'],BaseTask)
  if support_class is None:
    logInfo.TimeECHO("未支持的游戏类型type="+info['game'])
    return
  app = support_class(package_name, info['account'], info['link'],info['game'])
  # app.run()
  # app.stop()
  print("finished")


def get_all_subclasses(cls):
  """
  递归获取某个类的所有子类（包括直接和间接子类）
  """
  subclasses = set()
  for subclass in cls.__subclasses__():
    subclasses.add(subclass)
    subclasses.update(get_all_subclasses(subclass))  # 递归获取子类的子类
  return subclasses


def get_support_class(game_type, cls):
  classes = get_all_subclasses(cls)
  for subclass in classes:
    try:
      method = getattr(subclass, "is_support_game")
      if callable(method):  # 确保方法是可调用的
        is_support = method(game_type)
        if is_support:
          return subclass
    except Exception as e:
      print(f"Error executing method in {subclass.__name__}: {e}")

  return None


"""
逻辑就是
启动一个consumer进行一个个队列监听
每个消息有对于的游戏类型进行处理
消息队列只保护账号信息--提前和用户沟通扫码登录好

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
  # 通过配置文件参数启动，一个topic,一个实力
  # consumer = AutoConsumer(run_task, endpoint=endpoint, group="auto-games")
  mqdata = config.get("mqdata")
  data = json.dumps(mqdata)
  print("group=", data)
  run_task(data)
#   consumer.start("games","0")
