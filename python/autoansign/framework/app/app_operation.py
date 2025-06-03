# .......
import traceback

import numpy as np
from airtest.core.api import sleep
from airtest.core.api import start_app as start_app_o
from airtest.core.api import stop_app as stop_app_o

from framework.common.base_func import fun_name
from framework.common.common_func import connect_status
from framework.common.property import AutoProperty
from framework.common.settins import Settings

logInfo = AutoProperty.logInfo

class AppOB:
  def __init__(self, app_id="", big=False, device=None):
    # 配置device
    self.deviceOB = device
    self.big = big  # 是不是大型的程序, 容易卡顿，要多等待一会

    # 开始校验app_id
    self.app_id = app_id if len(app_id) > 0 else self.app_front()
    self.Activity = None if "/" not in self.app_id else self.app_id.split("/")[1]
    self.app_id = self.app_id.split("/")[0]
    #
    # 校验是否存在APP, 不同地区的app_id可能存在差异，例如mark.via, mark.via.gp
    self.HaveAPP = len(self.app_id) > 0
    self.APPlist = []
    if self.deviceOB:
      try:
        self.APPlist = self.deviceOB.device.list_app()
      except:
        logInfo.TimeECHO("appOB.获取app_list失败")
        self.APPlist = []
      if self.app_id not in self.APPlist and len(self.app_id) > 0:
        self.HaveAPP = False
        for app in self.APPlist:
          if self.app_id in str(app):
            logInfo.TimeECHO(f"更正app_id从{self.app_id}到{app}]")
            self.app_id = str(app)
            self.HaveAPP = True
            break
  #

  def app_start(self):
    if not self.HaveAPP:
      logInfo.TimeECHO(f"{fun_name(1)}:不存在{self.app_id},return")
      return False
    #
    if self.Activity:
      logInfo.TimeECHO(f"打开APP[{self.app_id}/{self.Activity}]中")
      success = start_app(self.app_id, self.Activity)
    else:
      logInfo.TimeECHO(f"打开APP[{self.app_id}]中")
      success = start_app(self.app_id)
    if not success:
      logInfo.TimeErr("打开失败,可能失联")
      return False
    else:
      sleep(20)
    return True

  def app_restart(self, sleeptime=0):
    if not self.HaveAPP:
      logInfo.TimeECHO(f"{fun_name(1)}:不存在{self.app_id},return")
      return False
    #
    logInfo.TimeECHO(f"重启APP中")
    self.app_close()
    sleep(10)
    sleeptime = max(10, sleeptime)  # 这里的单位是s
    printtime = max(30, sleeptime//10)
    if sleeptime > 60*60 and self.deviceOB:  # >1h
      self.deviceOB.device_reconnect(sleeptime)
    else:
      logInfo.TimeECHO("sleep %d min" % (sleeptime/60))
      nstep = int(sleeptime/printtime)
      for i in np.arange(nstep):
        logInfo.TimeECHO(f"...taskkill_sleep: {i}/{nstep}", end='\r')
        sleep(printtime)
    logInfo.TimeECHO(f"打开程序")
    if self.app_start():
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
      logInfo.TimeECHO(f"{fun_name(1)}:不存在{self.app_id},return")
      return False
    #
    logInfo.TimeECHO(f"关闭APP[{self.app_id}]中")
    if not stop_app(self.app_id):
      logInfo.TimeErr("关闭失败,可能失联")
      return False
    else:
      sleep(5)
      return True
  #

  def app_front(self, rebootimes=-1):
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
      if self.app_id in packageid:
        if len(self.app_id) != len(packageid):
          logInfo.TimeECHO(f"{fun_name(1)}: 注意app_id的差异 {self.app_id} vs {packageid}")
        return True
      else:
        logInfo.TimeECHO(f"{fun_name(1)}: 当前的前台APP is {packageid}")
        return False
    #
    # reboottimes > 0 则尝试校验前台APP是否和app_id相同
    # 只能解决程序闪退，无法处理，程序卡在开屏界面的情况
    logInfo.TimeECHO(f"{fun_name(1)}: 开始校验前台APP is {self.app_id}")
    logInfo.TimeECHO(f"{fun_name(1)}: 当前的前台APP is {packageid}")
    printinfo = f"{fun_name(1)}: 前台APP校验通过={packageid}"
    if self.app_id in packageid:
      logInfo.TimeECHO(printinfo)
      return self.app_id

    for i in range(6):
      logInfo.TimeECHO(f"{fun_name(1)}: 第{i+1}次打开APP")
      self.app_start()
      sleep(30)
      packageid = self.app_front()
      if self.app_id in packageid:
        printinfo = f"{fun_name(1)}: 前台APP校验通过={packageid}"
        logInfo.TimeECHO(printinfo)
        return self.app_id
      if i == 2:
        logInfo.TimeECHO(f"{fun_name(1)}: 重启设备后再次尝试")
        self.deviceOB.device_reconnect(10)
    #
    logInfo.TimeECHO(f"{fun_name(1)}"+">"*10)
    logInfo.TimeECHO(f"{fun_name(1)}: 前台APP校验失败,模拟器有问题")
    logInfo.TimeECHO(f"{fun_name(1)}"+"<"*10)
    #
    return self.app_front(rebootimes-1)


def start_app(*args, **kwargs):
  if AutoProperty.start_app_syskeys:
    args_list = list(args)
    args_list[0] = str(args_list[0])+" --pct-syskeys 0"
    args = args_list
    logInfo.TimeECHO(f"{fun_name(1)} with {args_list[0]}")
    AutoProperty.start_app_syskeys = True
  try:
    result = True
    start_app_o(*args, **kwargs)
  except:
    result = False
    logInfo.TimeECHO(f"{fun_name(1)} 失败")
    if not connect_status():
      logInfo.TimeErr(f"{fun_name(1)}连接不上设备")
      return result
    sleep(1)
    # ......
    # 安卓系统的报错, 尝试进行修复
    errormessgae = traceback.format_exc()
    if "AdbError" in errormessgae or True:
      """
      使用start_app启动安卓软件的各种坑（有的安卓系统使用monkey需要添加参数，否则报错）
      方式1(monkey). start_app(package_name), 需要修改Airtest的代码添加`--pct-syskeys 0`(https://cndaqiang.github.io/2023/11/10/MobileAuto/)
      adb -s 127.0.0.1:5555 shell monkey -p com.tencent.tmgp.sgame
      方式2(am start). start_app(package_name, activity)
      获得Activity的方法`adb -s 127.0.0.1:5565 shell dumpsys package com.tencent.tmgp.sgame`有一个Activity Resolver Table
      Airtest代码中是 adb -s 127.0.0.1:5565  shell am start -n package_name/package_name.activity
      可并不是所有的app的启动都遵循这一原则,如
      "com.tencent.tmgp.sgame/SGameActivity",
      "com.tencent.gamehelper.smoba/com.tencent.gamehelper.biz.launcher.ui.SplashActivit
      所以如果相同方式2，还是要修改Airtest的代码，变为package_name/activity
      综合上述原因，还是采取方式1, 添加`--pct-syskeys 0`
      虽然start_app(self.app_id)也能启动, 但是要修改代码airtest/core/android/adb.py,
      即使用start_app(self.app_id,Activity)就不用修改代码了
      """
      args_list = list(args)
      if args_list and "SYS_KEYS has no physical keys but with factor" in errormessgae:
        args_list = list(args)
        args_list[0] = str(args_list[0])+" --pct-syskeys 0"
        args = args_list
        logInfo.TimeECHO(f"{fun_name(1)} with {args_list[0]}")
        AutoProperty.start_app_syskeys = True
      if "device offline" in errormessgae:
        logInfo.TimeECHO("ADB device offline")
        return result
    # ......
    try:
      result = True
      start_app_o(*args, **kwargs)
    except:
      traceback.print_exc()
      logInfo.TimeECHO(f"再次尝试{fun_name(1)}仍失败，检测是否没有开启ADB,或者重新启动ADB")
      result = False
  return result



def stop_app(*args, **kwargs):
  try:
    result = True
    stop_app_o(*args, **kwargs)
  except:
    result = False
    logInfo.TimeECHO(f"{fun_name(1)} 失败")
    if not connect_status():
      logInfo.TimeErr(f"{fun_name(1)}连接不上设备")
      return result
    sleep(1)
    # 下面仍会输出信息，所以这里少报错，让屏幕更干净
    # traceback.print_exc()
    #
    try:
      result = True
      stop_app_o(*args, **kwargs)
    except:
      traceback.print_exc()
      logInfo.TimeECHO(f"再次尝试{fun_name(1)}仍失败")
      result = False
  return result


