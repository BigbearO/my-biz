# 基本类，包含常规模拟器地址,任务类型等
from abc import ABC

import pyautogui
from airtest.core.api import connect_device, sleep
from airtest.core.error import AirtestError

from framework.infra.common.auto_constants import DeviceTypeEnum, AutoConstants
from framework.task.ITask import ITask


# todo 之后加上重试这些机制
def connect(link):
  # todo 连接不上异常处理
  return  connect_device(link)

 # todo ios怎么支持
class BaseTask(ITask, ABC):
  def __init__(self, account, link, app_id):
    self.account = account
    self.link = link  # 连接的模拟器地址
    self.teams = []
    self.isMain = True  # 房主为true，否则为false
    self.game = app_id  # app包名
    self.task_mode = 1  # 任务类型，单人，组队
    self.device = connect(link)

  @classmethod
  def is_support_game(cls, game_type):
    game = cls.support_game()
    return game == game_type

  def simulator_hide(self):
    # keyboard.press_and_release()  # 按下boss键
    pyautogui.hotkey(AutoConstants.BossKey)


  def simulator_show(self):
    # keyboard.press_and_release(AutoConstants.BossKey)  # 按下boss键
    pyautogui.hotkey(AutoConstants.BossKey)

  def device_is_connect(self):
    if not self.device:
      return False

    try:
      self.device.check_app(self.game) # 检测是否安装了指定的apk
      return True
    except AirtestError:
      return True
    except Exception:
      return False

  def device_connect(self):
      self.device = connect(self.link)

  def app_start(self):
    self.device.start_app(self.game)

  def app_restart(self,  sleeptime=0):
    self.device.stop_app(self.game)  # 停止 App
    if sleeptime>0:
      sleep(sleeptime)
    self.device.start_app(self.game)  # 启动 App

  def app_stop(self):
    self.device.stop_app(self.game)

  def get_device_type(self):
    return self.device.get_current_platform().lower()

  def app_check_exist(self):
    device_type = self.get_device_type
    if DeviceTypeEnum.ANDROID.value == device_type:
      return self.device.check_app()
    else:
      # if DeviceTypeEnum.IOS.value==device_type:
        return False
  def exist_then_touch(self,temp):
    if self.device.exist(temp):
       self.device.touch()

  def install_app(self):
    pass

