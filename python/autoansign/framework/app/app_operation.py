# .......
import numpy as np
# 临时目录,用于存放临时文件
# 重写函数#
from airtest.core.api import sleep

from framework.app.base_app_func import start_app, stop_app
from framework.common.base_func import fun_name
from framework.common.property import AutoProperty

logInfo = AutoProperty.logInfo

class appOB:
  def __init__(self, APPID="", big=False, device=None):
    # 配置device
    self.deviceOB = device
    self.big = big  # 是不是大型的程序, 容易卡顿，要多等待一会
    #
    # 开始校验APPID
    self.APPID = APPID if len(APPID) > 0 else self.前台APP()
    self.Activity = None if "/" not in self.APPID else self.APPID.split("/")[1]
    self.APPID = self.APPID.split("/")[0]
    #
    # 校验是否存在APP, 不同地区的APPID可能存在差异，例如mark.via, mark.via.gp
    self.HaveAPP = len(self.APPID) > 0
    self.APPlist = []
    if self.deviceOB:
      try:
        self.APPlist = self.deviceOB.device.list_app()
      except:
        logInfo.TimeECHO("appOB.获取app_list失败")
        self.APPlist = []
      if self.APPID not in self.APPlist and len(self.APPID) > 0:
        self.HaveAPP = False
        for app in self.APPlist:
          if self.APPID in str(app):
            logInfo.TimeECHO(f"更正APPID从{self.APPID}到{app}]")
            self.APPID = str(app)
            self.HaveAPP = True
            break
  #

  def 打开APP(self):
    if not self.HaveAPP:
      logInfo.TimeECHO(f"{fun_name(1)}:不存在{self.APPID},return")
      return False
    #
    if self.Activity:
      logInfo.TimeECHO(f"打开APP[{self.APPID}/{self.Activity}]中")
      启动成功 = start_app(self.APPID, self.Activity)
    else:
      logInfo.TimeECHO(f"打开APP[{self.APPID}]中")
      启动成功 = start_app(self.APPID)
    if not 启动成功:
      logInfo.TimeErr("打开失败,可能失联")
      return False
    else:
      sleep(20)
    return True

  def app_restart(self, sleeptime=0):
    if not self.HaveAPP:
      logInfo.TimeECHO(f"{fun_name(1)}:不存在{self.APPID},return")
      return False
    #
    logInfo.TimeECHO(f"重启APP中")
    self.app_close()
    sleep(10)
    sleeptime = max(10, sleeptime)  # 这里的单位是s
    printtime = max(30, sleeptime/10)
    if sleeptime > 60*60 and self.deviceOB:  # >1h
      self.deviceOB.device_reconnect(sleeptime)
    else:
      logInfo.TimeECHO("sleep %d min" % (sleeptime/60))
      nstep = int(sleeptime/printtime)
      for i in np.arange(nstep):
        logInfo.TimeECHO(f"...taskkill_sleep: {i}/{nstep}", end='\r')
        sleep(printtime)
    logInfo.TimeECHO(f"打开程序")
    if self.打开APP():
      if self.big:
        logInfo.TimeECHO(f"打开程序成功,sleep60*2")
        sleep(60*2)
      return True
    else:
      logInfo.TimeECHO(f"打开程序失败")
      return False
  #

  def app_close(self):
    if not self.HaveAPP:
      logInfo.TimeECHO(f"{fun_name(1)}:不存在{self.APPID},return")
      return False
    #
    logInfo.TimeECHO(f"关闭APP[{self.APPID}]中")
    if not stop_app(self.APPID):
      logInfo.TimeErr("关闭失败,可能失联")
      return False
    else:
      sleep(5)
      return True
  #

  def 前台APP(self, rebootimes=-1):
    #
    # only support android
    if not self.deviceOB:
      return ""
    if "android" not in self.deviceOB.device_type:
      logInfo.TimeECHO(f"{fun_name(1)}不支持{self.deviceOB.device_type}")
      return ""
    #
    try:
      # airtest提供了一种查询的方法: adb shell "dumpsys activity top | grep ACTIVI"
      # 这个命令会返回所有的活动APP, airtest返回最后一个活动的APP
      # 目前适配我的android 8 和模拟器, 便不再自己造轮子了
      # 后续可以在这里更新查询方法
      packageid = self.deviceOB.device.get_top_activity_name()
      packageid = packageid.split("/")[0]
    except:
      packageid = None
    #
    packageid = packageid if packageid else ""
    # Default, 小于0, 返回当前ID
    if rebootimes < 0:
      return packageid if packageid else ""
    # 等于0, 只判断是否和默认ID相同
    if rebootimes == 0:
      if self.APPID in packageid:
        if len(self.APPID) != len(packageid):
          logInfo.TimeECHO(f"{fun_name(1)}: 注意APPID的差异 {self.APPID} vs {packageid}")
        return True
      else:
        logInfo.TimeECHO(f"{fun_name(1)}: 当前的前台APP is {packageid}")
        return False
    #
    # reboottimes > 0 则尝试校验前台APP是否和APPID相同
    # 只能解决程序闪退，无法处理，程序卡在开屏界面的情况
    logInfo.TimeECHO(f"{fun_name(1)}: 开始校验前台APP is {self.APPID}")
    logInfo.TimeECHO(f"{fun_name(1)}: 当前的前台APP is {packageid}")
    printinfo = f"{fun_name(1)}: 前台APP校验通过={packageid}"
    if self.APPID in packageid:
      logInfo.TimeECHO(printinfo)
      return self.APPID

    for i in range(6):
      logInfo.TimeECHO(f"{fun_name(1)}: 第{i+1}次打开APP")
      self.打开APP()
      sleep(30)
      packageid = self.前台APP()
      if self.APPID in packageid:
        printinfo = f"{fun_name(1)}: 前台APP校验通过={packageid}"
        logInfo.TimeECHO(printinfo)
        return self.APPID
      if i == 2:
        logInfo.TimeECHO(f"{fun_name(1)}: 重启设备后再次尝试")
        self.deviceOB.device_reconnect(10)
    #
    logInfo.TimeECHO(f"{fun_name(1)}"+">"*10)
    logInfo.TimeECHO(f"{fun_name(1)}: 前台APP校验失败,模拟器有问题")
    logInfo.TimeECHO(f"{fun_name(1)}"+"<"*10)
    #
    return self.前台APP(rebootimes-1)

