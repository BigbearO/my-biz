import os
import sys
from datetime import timezone, timedelta
from airtest.core.api import Template as Template_o

class AutoConstants:
  start_app_syskeys = False
  current_file_path = os.path.abspath(__file__)
  current_dir = os.path.dirname(current_file_path)
  testpng = Template_o(os.path.join(current_dir, "tpl_target_pos.png"), record_pos=(-0.28, 0.153), resolution=(960, 540), target_pos=6)
  figdir = "assets"  # 输入的静态(图片)资源目录
  figdirs = [figdir]  # 默认的静态(图片)资源目录, 输入的优先级 figdir > "./" >  figdirs.append()
  BossKeydict = {18: "alt", 17: "ctrl", 16: "shift", 88: "x", 81: "q"}
  BossKey = {0: []}  # 老板键,快速隐藏APP

  # 控制端
  platform = sys.platform.lower()
  # 避免和windows名字接近
  platform = "macos" if "darwin" in platform else platform



class TimeZoneInfo:
  def __init__(self):
    self.mobiletime = 8
    self.eastern_eight_offset = timedelta(hours=self.mobiletime)
    self.eastern_eight_tz = timezone(self.eastern_eight_offset)
