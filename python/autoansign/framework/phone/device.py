
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# .......
import os
import shlex
import sys
import traceback

import numpy as np
# 临时目录,用于存放临时文件
# 重写函数#
from airtest.core.api import connect_device, sleep
from airtest.core.settings import Settings

from framework.common.auto_constants import AutoConstants
from framework.common.base_func import fun_name, funs_name, getPopen
from framework.common.common_func import run_command, touchkey_win, swipe, \
  getpid_win
from framework.common.property import AutoProperty
from framework.phone.dqWheel import DQWheel

logInfo = AutoProperty.logInfo

class deviceOB:
  def __init__(self, device_type=None, teams=None, link="Android:///"+"127.0.0.1:"+str(5555)):
    # 控制端
    self.controller = sys.platform.lower()
    # 避免和windows名字接近
    self.controller = "macos" if "darwin" in self.controller else self.controller
    # 客户端
    self.device = None
    self.teams=teams
    #
    self.LINK = link
    self.LINKport = self.LINK.split(":")[-1]  # port
    # (USB连接时"Android:///id",没有端口
    self.LINKport = "" if "/" in self.LINKport else self.LINKport
    self.LINKtype = self.LINK.split(":")[0].lower()  # android, ios
    self.LINKhead = self.LINK[:-len(self.LINKport)-1] if len(self.LINKport) > 0 else self.LINK  # ios:///http:ip
    self.LINKURL = self.LINK.split("/")[-1]  # ip:port
    self.device_type = device_type.lower() if device_type else self.LINKtype
    #
    self.adb_path = "adb"
    if "android" in self.device_type:
      from airtest.core.android import adb
      self.ADB = adb.ADB()
      self.adb_path = self.ADB.adb_path
    # 不同客户端对重启的适配能力不同
    if "ios" in self.device_type:
      self.client = "ios"
    # 自定义安卓设备/模拟器
    # elif self.link in Settings.cmd_start_device.keys() or self.link in AutoPropery.cmd_stop_device.keys():
    #   self.client = "UserDefinedAndroid"
    elif "win" in self.controller:
      # if os.path.exists(Settings.BlueStackdir) and self.link in AutoProperty.win_Instance.keys():
      #   self.client = "win_BlueStacks"
      # elif os.path.exists(AutoProperty.LDPlayerdir) and self.link in AutoProperty.win_Instance.keys():
      #   self.client = "win_LD"
      # elif os.path.exists(Settings.MuMudir) and self.link in AutoProperty.win_Instance.keys():
      #   self.client = "win_MuMu"
      if len(self.LINKport) > 0:  # 通过网络访问的安卓设备
        self.client = "RemoteAndroid"
      else:
        self.client = "USBAndroid"
    elif "linux" in self.controller and self.LINK in AutoProperty.dockercontain.keys():  # Linux + docker
      self.client = "lin_docker"
    elif len(self.LINKport) > 0:  # 通过网络访问的安卓设备
      self.client = "RemoteAndroid"
    else:
      self.client = "USBAndroid"

    #实例终端
    self.instance_terminal = False
    self.instance_terminal = "mac" in self.controller or "ios" in self.device_type
    #
    logInfo.TimeECHO(f"控制端({self.controller})")
    logInfo.TimeECHO(f"客户端({self.client})")
    logInfo.TimeECHO(f"ADB =({self.adb_path})")
    logInfo.TimeECHO(f"LINK({self.LINK})")
    logInfo.TimeECHO(f"LINKhead({self.LINKhead})")
    logInfo.TimeECHO(f"LINKtype({self.LINKtype})")
    logInfo.TimeECHO(f"LINKURL({self.LINKURL})")
    logInfo.TimeECHO(f"LINKport({self.LINKport})")
    #
    self.device_connect()

  def device_info(self):
    self.display_info = {}
    if self.device:
      self.display_info = self.device.display_info
      self.width = self.display_info["width"]
      self.height = self.display_info["height"]
      self.resolution = (self.width, self.height)

  def device_connect(self, times=1, timesMax=2):
    """
    # 尝试连接timesMax+1次,当前是times次
    # 在timesMax次时，会尝试重启设备
    """
    self.device = False
    #
    logInfo.TimeECHO(f"{self.LINK}:开始第{times}/{timesMax+1}次连接")
    try:
      self.device = connect_device(self.LINK)
      if self.device:
        logInfo.TimeECHO(f"{self.LINK}:链接成功")
        self.device_info()
        return True
    except:
      if times == timesMax+1:
        traceback.print_exc()
      logInfo.TimeErr(f"{self.LINK}:链接失败")
      if "ios" in self.device_type:
        logInfo.TimeECHO("重新插拔数据线")
      if "android" in self.LINKtype:
        # 检查adb的执行权限
        result = getPopen([self.adb_path])
        if "Android" in result[1]:
          result = getPopen([self.adb_path, "devices"])
          if self.LINKURL not in result[1]:
            logInfo.TimeECHO(f"没有找到ADB设备{self.LINKURL}\n"+result[1])
            if times == timesMax:
              self.device_reboot()
              return self.device_connect(times+1, timesMax)
        else:
          logInfo.TimeErr(f"{self.adb_path} 执行错误")
          logInfo.TimeErr(result[1])
          return False
    #
    if times < timesMax:
      logInfo.TimeECHO(f"{self.LINK}:链接失败,启动设备再次连接")
      self.device_poweron()
      return self.device_connect(times+1, timesMax)
    elif times == timesMax:
      logInfo.TimeECHO(f"{self.LINK}:链接失败,重启设备再次连接")
      self.device_reboot()
      return self.device_connect(times+1, timesMax)
    else:
      logInfo.TimeErr(f"{self.LINK}:链接失败次数达到上限{timesMax},无法继续")
      return False

  def device_poweron(self):
    if "android" in self.LINKtype:
      # 避免其他adb程序占用导致卡住
      run_command([[str(self.adb_path), "disconnect", self.LINKURL]])
    command = []
    logInfo.TimeECHO(f"尝试启动设备中...")
    if self.client == "ios":
      if "mac" in self.controller:
        logInfo.TimeECHO(f"测试本地IOS打开中")
      else:
        logInfo.TimeECHO(f"当前模式无法打开IOS")
        return False
      # 获得运行的结果
      result = getPopen(["tidevice", "list"])
      if 'ConnectionType.USB' in result[1]:
        # wdaproxy这个命令会同时调用xctest和relay，另外当wda退出时，会自动重新启动xctest
        # tidevice不支持企业签名的WDA
        self.LINKport = str(int(self.LINKport)+1)
        self.LINK = self.LINKhead+":"+self.LINKport
        # @todo, 此命令没有经过测试
        command.append([f"tidevice $(cat para.txt) wdaproxy -B  com.facebook.WebDriverAgentRunner.cndaqiang.xctrunner --port {self.LINKport} > tidevice.result.txt 2 > &1 &"])
        sleep(20)
      else:
        logInfo.logInfo.TimeErr(" tidevice list 无法找到IOS设备重启失败")
        return False
    # android
    elif self.client == "UserDefinedAndroid":
      if self.link in AutoProperty.cmd_start_device.keys():
        command.append(shlex.split(AutoProperty.cmd_start_device[self.link]))
      else:
        command.append([self.adb_path, "connect", self.LINKURL])
    # elif self.client == "win_BlueStacks":
    #   instance = str(AutoProperty.win_Instance[self.link])
    #   command.append([os.path.join(Settings.BlueStackdir, "HD-Player.exe"), "--instance", instance])
    # elif self.client == "win_LD":
    #   instance = str(AutoProperty.win_Instance[self.link])
    #   command.append([os.path.join(AutoProperty.LDPlayerdir, "ldconsole.exe"), "launch", "--index", instance])
    # elif self.client == "win_MuMu":
    #   instance = str(AutoProperty.win_Instance[self.link])
    #   command.append([os.path.join(Settings.MuMudir, "MuMuManager.exe"), "control", "-v", instance, "launch"])
    elif self.client == "FULL_ADB":
      # 通过reboot的方式可以实现重启和解决资源的效果
      command.append([self.adb_path, "connect", self.LINKURL])
      command.append([self.adb_path, "-s", self.LINKURL, "reboot"])
    elif self.client == "lin_docker":
      containID = f"{AutoProperty.dockercontain[self.link]}"
      command.append(["docker", "restart", containID])
    elif True:
      command.append([self.adb_path, "connect", self.LINKURL])
    # 下面的命令这两个命令是需要root权限的, 或者不对所有设备适配的
    # 可能无法执行, 因此默认关闭了, 未来可以考虑启动这些命令
    elif self.client == "RemoteAndroid":
      # 热重启系统，需要root权限
      command.append([self.adb_path, "-s", self.LINKURL, "shell", "stop"])
      command.append([self.adb_path, "-s", self.LINKURL, "shell", "start"])
    elif self.client == "USBAndroid":
      result = getPopen([self.adb_path, "devices"])
      if self.LINKURL in result[1]:
        command.append([self.adb_path, "-s", self.LINKURL, "reboot"])
      else:
        logInfo.TimeECHO(f"没有找到USB设备{self.LINKURL}\n"+result[1])
        return False
    else:
      logInfo.TimeECHO(f"未知device_type")
      return False
    #
    # 开始运行
    # BossKey
    if self.link in AutoConstants.BossKey.keys():
      BossKey = AutoConstants.BossKey[self.link]
    else:
      BossKey = []
    #
    if len(BossKey) > 0:
      # 交叉启动模拟器, 避免快捷键冲突
      hour, minu, sec = DQWheel.time_getHMS()
      while minu % (len(self.teams)) != self.link or sec > 30:
        if len(self.teams) == 1:
          break
        logInfo.TimeECHO("等待启动时间中")
        sleep(5)
        hour, minu, sec = DQWheel.time_getHMS()
      # 在启动新的模拟器之前，按下Bosskey把所有的模拟器都调到前台
      touchkey_win(BossKey)
      sleep(5)
    #
    exit_code = run_command(command=command)
    #
    # 让客户端在后台运行
    touchkey_win(BossKey)
    #
    if exit_code == 0:
      logInfo.TimeECHO(f"启动成功")
      return True
    else:
      logInfo.TimeErr(f"启动失败")
      return False

  def device_shutdow(self):
    command = []
    logInfo.TimeECHO(f"尝试关闭设备中...")
    if self.client == "ios":
      if "mac" in self.controller:
        logInfo.TimeECHO(f"测试本地IOS关闭中")
        command.append(["tidevice", "reboot"])
      else:
        logInfo.TimeECHO(f"当前模式无法关闭IOS")
        return False
    # android
    elif self.client == "UserDefinedAndroid":
      if self.link in AutoProperty.cmd_stop_device.keys():
        command.append(shlex.split(AutoProperty.cmd_stop_device[self.link]))
      else:
        command.append([self.adb_path, "disconnect", self.LINKURL])
    elif self.client == "win_BlueStacks":
      # 尝试获取PID
      PID = getpid_win(IMAGENAME="HD-Player.exe", key=AutoProperty.win_InstanceName[self.link])
      if PID <= 0:
        logInfo.TimeECHO("如果BlueStacks在后台时，需要先调到前台才能获得准确的PID")
        if self.link in AutoConstants.BossKey.keys():
          BossKey = AutoConstants.BossKey[self.link]
        else:
          BossKey = []
        if len(BossKey) > 0:
          # 交叉关闭模拟器, 避免快捷键冲突
          hour, minu, sec = DQWheel.time_getHMS()
          while minu % (len(self.teams)) != self.link or sec > 30:
            if len(self.teams) == 1:
              break
            logInfo.TimeECHO("等待关闭时间中")
            sleep(5)
            hour, minu, sec = DQWheel.time_getHMS()
          # 在获得模拟器名称前，按下Bosskey把所有的模拟器都调到前台
          touchkey_win(BossKey)
          sleep(5)
          PID = getpid_win(IMAGENAME="HD-Player.exe", key=AutoProperty.win_InstanceName[self.link])
          touchkey_win(BossKey)
      if PID > 0:
        command.append(["taskkill", "/F", "/FI", f"PID eq {str(PID)}"])
      else:
        # 关闭所有虚拟机，暂时不采用
        # command.append(["taskkill", "/F", "/IM", "HD-Player.exe"])
        logInfo.TimeErr("找不到BlueStacks模拟器窗口, 请检查配置参数BlueStack_Windows与模拟器名称是否一一对应")
        command = []
    elif self.client == "win_LD":
      instance = AutoProperty.win_Instance[self.link]
      command.append([os.path.join(AutoProperty.LDPlayerdir, "ldconsole.exe"), "quit", "--index", instance])
    elif self.client == "win_MuMu":
      instance = AutoProperty.win_Instance[self.link]
      command.append([os.path.join(AutoProperty.MuMudir, "MuMuManager.exe"), "control", "-v", instance, "shutdown"])
    elif self.client == "lin_docker":
      containID = f"{AutoProperty.dockercontain[self.link]}"
      command.append(["docker", "stop", containID])
    elif True:
      command.append([self.adb_path, "disconnect", self.LINKURL])
    # 下面的命令这两个命令是需要root权限的, 或者不对所有设备适配的
    # 可能无法执行, 因此默认关闭了, 未来可以考虑启动这些命令
    elif self.client == "RemoteAndroid":
      # 热重启系统，需要root权限
      command.append([self.adb_path, "-s", self.LINKURL, "shell", "stop"])
      command.append([self.adb_path, "-s", self.LINKURL, "shell", "start"])
      command.append([self.adb_path, "disconnect", self.LINKURL])
    elif self.client == "USBAndroid":
      result = getPopen([self.adb_path, "devices"])
      if self.LINKURL in result[1]:
        command.append([self.adb_path, "-s", self.LINKURL, "reboot"])
      else:
        logInfo.TimeECHO(f"没有找到USB设备{self.LINKURL}\n"+result[1])
        command = []
    elif self.client == "FULL_ADB":
      # 通过reboot的方式可以实现重启和解决资源的效果
      command.append([self.adb_path, "connect", self.LINKURL])
      command.append([self.adb_path, "-s", self.LINKURL, "reboot"])
    #
    # 开始运行
    exit_code = run_command(command=command, sleeptime=60)
    if exit_code == 0:
      logInfo.TimeECHO(f"关闭成功")
      return True
    else:
      logInfo.TimeECHO(f"关闭失败")
      return False

  def device_reboot(self, sleeptime=0):
    logInfo.TimeECHO(f"重新启动({self.LINK})")
    self.device_shutdow()
    sleeptime = max(10, sleeptime-60)
    printtime = max(30, sleeptime/10)
    logInfo.TimeECHO("sleep %d min" % (sleeptime/60))
    for i in np.arange(int(sleeptime/printtime)):
      logInfo.TimeECHO(f"...taskkill_sleep: {i}", end='\r')
      sleep(printtime)
    self.device_poweron()

  def device_reconnect(self, sleeptime=0):
    self.device_reboot(sleeptime=sleeptime)
    return self.device_connect()

  def device_onlock(self, sleeptime=0):
    success = not self.device.is_locked()
    if not success:
      logInfo.TimeECHO("屏幕已锁定")
      # 先唤醒
      self.device.wake()
      # 再向上滑动解锁
      if not "width" in self.display_info.keys():
        self.device_info()
      w = self.display_info["width"]
      h = self.display_info["height"]
      swipe((w/2, h*0.9), (w/2, h*0.5))
      # 尝试用airtest的解锁. 很容易失败
      if self.device.is_locked():
        logInfo.TimeECHO("滑动解锁失败")
        self.device.unlock()
        if self.device.is_locked():
          logInfo.TimeECHO("unlock()方式解锁失败")
      #
      success = not self.device.is_locked()
      if success:
        logInfo.TimeECHO(f"解锁成功")
    return success
  # 返回键
  def device_return_back(self):
    self.device.keyevent("BACK")
    logInfo.TimeECHO(f"TOUCH.返回键")
    sleep(0.5)
    return

  def home(self):
    self.device.keyevent("HOME")
    logInfo.TimeECHO(f"TOUCH.HOMOE键")
    sleep(0.5)
    return
  #

  def shell(self, command=[], sleeptime=20,  quiet=False, must_ok=False):
    # 暂时仅支持android
    """
    adb shell ...
    """
    if not self.LINKtype == "android":
      logInfo.TimeECHO(f"{fun_name(1)}暂不支持{self.LINKtype}")
      return -100
    #
    exit_code_o = 0
    command_step = 0
    # 获得运行的结果
    if not quiet:
      logInfo.TimeECHO(funs_name())
    for i_command in command:
      if len(i_command) < 1:
        continue
      # 去掉所有的空白符号看是否还有剩余命令
      trim_insert = shlex.join(i_command).strip()
      if len(trim_insert) < 1:
        continue
      if not quiet:
        logInfo.TimeECHO("  devices shell:"+trim_insert)
      try:
        result = [0, str(self.device.adb.shell(i_command))]
      except:
        result = [1, traceback.format_exc()]
      command_step = command_step + 1
      exit_code = result[0]
      if not quiet:
        if exit_code != 0:
          logInfo.TimeECHO("result:"+">"*20)
          logInfo.TimeECHO(result[1])
          logInfo.TimeECHO("result:"+"<"*20)
      exit_code_o += exit_code
      if must_ok and exit_code_o != 0:
        break
      sleep(sleeptime)
    # 没有执行任何命令
    if command_step == 0:
      exit_code_o = -100
    return exit_code_o

  def open_url_link(self, url="https://cndaqiang.github.io"):
    """
    adb shell am start -a android.intent.action.VIEW -d  https://cndaqiang.github.io
    self.device.adb.shell(['am', 'start', '-a', 'android.intent.action.VIEW','-d', url ])
    """
    if len(url) < 1:
      return
    command = []
    if self.LINKtype == "android":
      command.append(['am', 'start', '-a', 'android.intent.action.VIEW', '-d', url])
    else:
      logInfo.TimeECHO(f"{fun_name(1)}暂不支持{self.LINKtype}")
      return -100
    return self.shell(command)

