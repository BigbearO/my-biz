import os
import sys
from datetime import timezone, timedelta
from airtest.core.api import Template as Template_o
from enum import Enum

class AutoConstants:
  start_app_syskeys = False
  current_file_path = os.path.abspath(__file__)
  current_dir = os.path.dirname(current_file_path)
  # todo 这个路径可以改一下
  testpng = Template_o(os.path.join(current_dir, "tpl_target_pos.png"), record_pos=(-0.28, 0.153), resolution=(960, 540), target_pos=6)
  figdir = "assets"  # 输入的静态(图片)资源目录
  figdirs = [figdir]  # 默认的静态(图片)资源目录, 输入的优先级 figdir > "./" >  figdirs.append()
  BossKeydict = {18: "alt", 17: "ctrl", 16: "shift", 88: "x", 81: "q"}
  # todo 序列化
  BossKey = ['alt', 'q']  # 老板键,快速隐藏APP
  # todo 更改合适的包名
  connect_check_package="com.yuliang.wuziqigame"
  # 控制端
  platform = sys.platform.lower()
  # 避免和windows名字接近
  platform = "macos" if "darwin" in platform else platform

class PlatformEnum(Enum):
  WIN="windows"
  MAC="darwin" #上面的经过了转换不然应该是 darwin
  LINUX="linux"

class DeviceTypeEnum(Enum):
  ANDROID="android"
  IOS="ios"


class TimeZoneInfo:
  def __init__(self):
    self.mobiletime = 8
    self.eastern_eight_offset = timedelta(hours=self.mobiletime)
    self.eastern_eight_tz = timezone(self.eastern_eight_offset)
