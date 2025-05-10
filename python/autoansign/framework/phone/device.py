
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

##################################
# Author : cndaqiang             #
# Update : 2023-11-10            #
# Build  : 2023-11-10            #
# What   : IOS/Android 自动化任务  #
#################################
# .......
from datetime import datetime, timezone, timedelta
import time
from airtest.core.settings import Settings as ST
import logging
import sys
import os
import numpy as np
import random
import traceback
import subprocess
import shlex
import multiprocessing
import yaml
# 临时目录,用于存放临时文件
import tempfile
# 重写函数#
from airtest.core.api import connect_device, sleep
from airtest.core.api import exists as exists_o
from airtest.core.api import touch as touch_o
from airtest.core.api import swipe as swipe_o
from airtest.core.api import start_app as start_app_o
from airtest.core.api import stop_app as stop_app_o
from airtest.core.api import Template as Template_o

class deviceOB:
  def __init__(self, 设备类型=None, mynode=0, totalnode=1, LINK="Android:///"+"127.0.0.1:"+str(5555)):
    # 控制端
    self.控制端 = sys.platform.lower()
    # 避免和windows名字接近
    self.控制端 = "macos" if "darwin" in self.控制端 else self.控制端
    #
    # 客户端
    self.device = None
    #
    self.mynode = mynode
    self.totalnode = totalnode
    self.LINK = LINK
    self.LINKport = self.LINK.split(":")[-1]  # port
    # (USB连接时"Android:///id",没有端口
    self.LINKport = "" if "/" in self.LINKport else self.LINKport
    self.LINKtype = self.LINK.split(":")[0].lower()  # android, ios
    self.LINKhead = self.LINK[:-len(self.LINKport)-1] if len(self.LINKport) > 0 else self.LINK  # ios:///http:ip
    self.LINKURL = self.LINK.split("/")[-1]  # ip:port
    self.设备类型 = 设备类型.lower() if 设备类型 else self.LINKtype
    #
    self.adb_path = "adb"
    if "android" in self.设备类型:
      from airtest.core.android import adb
      self.ADB = adb.ADB()
      self.adb_path = self.ADB.adb_path
    # 不同客户端对重启的适配能力不同
    if "ios" in self.设备类型:
      self.客户端 = "ios"
    # 自定义安卓设备/模拟器
    elif self.mynode in Settings.cmd_start_device.keys() or self.mynode in Settings.cmd_stop_device.keys():
      self.客户端 = "UserDefinedAndroid"
    elif "win" in self.控制端:
      if os.path.exists(Settings.BlueStackdir) and self.mynode in Settings.win_Instance.keys():
        self.客户端 = "win_BlueStacks"
      elif os.path.exists(Settings.LDPlayerdir) and self.mynode in Settings.win_Instance.keys():
        self.客户端 = "win_LD"
      elif os.path.exists(Settings.MuMudir) and self.mynode in Settings.win_Instance.keys():
        self.客户端 = "win_MuMu"
      elif len(self.LINKport) > 0:  # 通过网络访问的安卓设备
        self.客户端 = "RemoteAndroid"
      else:
        self.客户端 = "USBAndroid"
    elif "linux" in self.控制端 and self.mynode in Settings.dockercontain.keys():  # Linux + docker
      self.客户端 = "lin_docker"
    elif len(self.LINKport) > 0:  # 通过网络访问的安卓设备
      self.客户端 = "RemoteAndroid"
    else:
      self.客户端 = "USBAndroid"
    #

    #
    self.实体终端 = False
    self.实体终端 = "mac" in self.控制端 or "ios" in self.设备类型
    #
    TimeECHO(f"控制端({self.控制端})")
    TimeECHO(f"客户端({self.客户端})")
    TimeECHO(f"ADB =({self.adb_path})")
    TimeECHO(f"LINK({self.LINK})")
    TimeECHO(f"LINKhead({self.LINKhead})")
    TimeECHO(f"LINKtype({self.LINKtype})")
    TimeECHO(f"LINKURL({self.LINKURL})")
    TimeECHO(f"LINKport({self.LINKport})")
    #
    self.连接设备()

  def 设备信息(self):
    self.display_info = {}
    if self.device:
      self.display_info = self.device.display_info
      self.width = self.display_info["width"]
      self.height = self.display_info["height"]
      self.resolution = (self.width, self.height)

  def 连接设备(self, times=1, timesMax=2):
    """
    # 尝试连接timesMax+1次,当前是times次
    # 在timesMax次时，会尝试重启设备
    """
    self.device = False
    #
    TimeECHO(f"{self.LINK}:开始第{times}/{timesMax+1}次连接")
    try:
      self.device = connect_device(self.LINK)
      if self.device:
        TimeECHO(f"{self.LINK}:链接成功")
        self.设备信息()
        return True
    except:
      if times == timesMax+1:
        traceback.print_exc()
      TimeErr(f"{self.LINK}:链接失败")
      if "ios" in self.设备类型:
        TimeECHO("重新插拔数据线")
      if "android" in self.LINKtype:
        # 检查adb的执行权限
        result = getPopen([self.adb_path])
        if "Android" in result[1]:
          result = getPopen([self.adb_path, "devices"])
          if self.LINKURL not in result[1]:
            TimeECHO(f"没有找到ADB设备{self.LINKURL}\n"+result[1])
            if times == timesMax:
              self.重启设备()
              return self.连接设备(times+1, timesMax)
        else:
          TimeErr(f"{self.adb_path} 执行错误")
          TimeErr(result[1])
          return False
    #
    if times < timesMax:
      TimeECHO(f"{self.LINK}:链接失败,启动设备再次连接")
      self.启动设备()
      return self.连接设备(times+1, timesMax)
    elif times == timesMax:
      TimeECHO(f"{self.LINK}:链接失败,重启设备再次连接")
      self.重启设备()
      return self.连接设备(times+1, timesMax)
    else:
      TimeErr(f"{self.LINK}:链接失败次数达到上限{timesMax},无法继续")
      return False

  def 启动设备(self):
    if "android" in self.LINKtype:
      # 避免其他adb程序占用导致卡住
      run_command([[str(self.adb_path), "disconnect", self.LINKURL]])
    command = []
    TimeECHO(f"尝试启动设备中...")
    if self.客户端 == "ios":
      if "mac" in self.控制端:
        TimeECHO(f"测试本地IOS打开中")
      else:
        TimeECHO(f"当前模式无法打开IOS")
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
        TimeErr(" tidevice list 无法找到IOS设备重启失败")
        return False
    # android
    elif self.客户端 == "UserDefinedAndroid":
      if self.mynode in Settings.cmd_start_device.keys():
        command.append(shlex.split(Settings.cmd_start_device[self.mynode]))
      else:
        command.append([self.adb_path, "connect", self.LINKURL])
    elif self.客户端 == "win_BlueStacks":
      instance = str(Settings.win_Instance[self.mynode])
      command.append([os.path.join(Settings.BlueStackdir, "HD-Player.exe"), "--instance", instance])
    elif self.客户端 == "win_LD":
      instance = str(Settings.win_Instance[self.mynode])
      command.append([os.path.join(Settings.LDPlayerdir, "ldconsole.exe"), "launch", "--index", instance])
    elif self.客户端 == "win_MuMu":
      instance = str(Settings.win_Instance[self.mynode])
      command.append([os.path.join(Settings.MuMudir, "MuMuManager.exe"), "control", "-v", instance, "launch"])
    elif self.客户端 == "FULL_ADB":
      # 通过reboot的方式可以实现重启和解决资源的效果
      command.append([self.adb_path, "connect", self.LINKURL])
      command.append([self.adb_path, "-s", self.LINKURL, "reboot"])
    elif self.客户端 == "lin_docker":
      containID = f"{Settings.dockercontain[self.mynode]}"
      command.append(["docker", "restart", containID])
    elif True:
      command.append([self.adb_path, "connect", self.LINKURL])
    # 下面的命令这两个命令是需要root权限的, 或者不对所有设备适配的
    # 可能无法执行, 因此默认关闭了, 未来可以考虑启动这些命令
    elif self.客户端 == "RemoteAndroid":
      # 热重启系统，需要root权限
      command.append([self.adb_path, "-s", self.LINKURL, "shell", "stop"])
      command.append([self.adb_path, "-s", self.LINKURL, "shell", "start"])
    elif self.客户端 == "USBAndroid":
      result = getPopen([self.adb_path, "devices"])
      if self.LINKURL in result[1]:
        command.append([self.adb_path, "-s", self.LINKURL, "reboot"])
      else:
        TimeECHO(f"没有找到USB设备{self.LINKURL}\n"+result[1])
        return False
    else:
      TimeECHO(f"未知设备类型")
      return False
    #
    # 开始运行
    # BossKey
    if self.mynode in Settings.BossKey.keys():
      BossKey = Settings.BossKey[self.mynode]
    else:
      BossKey = []
    #
    if len(BossKey) > 0:
      # 交叉启动模拟器, 避免快捷键冲突
      hour, minu, sec = DQWheel.time_getHMS()
      while minu % (self.totalnode) != self.mynode or sec > 30:
        if self.totalnode == 1:
          break
        TimeECHO("等待启动时间中")
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
      TimeECHO(f"启动成功")
      return True
    else:
      TimeErr(f"启动失败")
      return False

  def 关闭设备(self):
    command = []
    TimeECHO(f"尝试关闭设备中...")
    if self.客户端 == "ios":
      if "mac" in self.控制端:
        TimeECHO(f"测试本地IOS关闭中")
        command.append(["tidevice", "reboot"])
      else:
        TimeECHO(f"当前模式无法关闭IOS")
        return False
    # android
    elif self.客户端 == "UserDefinedAndroid":
      if self.mynode in Settings.cmd_stop_device.keys():
        command.append(shlex.split(Settings.cmd_stop_device[self.mynode]))
      else:
        command.append([self.adb_path, "disconnect", self.LINKURL])
    elif self.客户端 == "win_BlueStacks":
      # 尝试获取PID
      PID = getpid_win(IMAGENAME="HD-Player.exe", key=Settings.win_InstanceName[self.mynode])
      if PID <= 0:
        TimeECHO("如果BlueStacks在后台时，需要先调到前台才能获得准确的PID")
        if self.mynode in Settings.BossKey.keys():
          BossKey = Settings.BossKey[self.mynode]
        else:
          BossKey = []
        if len(BossKey) > 0:
          # 交叉关闭模拟器, 避免快捷键冲突
          hour, minu, sec = DQWheel.time_getHMS()
          while minu % (self.totalnode) != self.mynode or sec > 30:
            if self.totalnode == 1:
              break
            TimeECHO("等待关闭时间中")
            sleep(5)
            hour, minu, sec = DQWheel.time_getHMS()
          # 在获得模拟器名称前，按下Bosskey把所有的模拟器都调到前台
          touchkey_win(BossKey)
          sleep(5)
          PID = getpid_win(IMAGENAME="HD-Player.exe", key=Settings.win_InstanceName[self.mynode])
          touchkey_win(BossKey)
      if PID > 0:
        command.append(["taskkill", "/F", "/FI", f"PID eq {str(PID)}"])
      else:
        # 关闭所有虚拟机，暂时不采用
        # command.append(["taskkill", "/F", "/IM", "HD-Player.exe"])
        TimeErr("找不到BlueStacks模拟器窗口, 请检查配置参数BlueStack_Windows与模拟器名称是否一一对应")
        command = []
    elif self.客户端 == "win_LD":
      instance = Settings.win_Instance[self.mynode]
      command.append([os.path.join(Settings.LDPlayerdir, "ldconsole.exe"), "quit", "--index", instance])
    elif self.客户端 == "win_MuMu":
      instance = Settings.win_Instance[self.mynode]
      command.append([os.path.join(Settings.MuMudir, "MuMuManager.exe"), "control", "-v", instance, "shutdown"])
    elif self.客户端 == "lin_docker":
      containID = f"{Settings.dockercontain[self.mynode]}"
      command.append(["docker", "stop", containID])
    elif True:
      command.append([self.adb_path, "disconnect", self.LINKURL])
    # 下面的命令这两个命令是需要root权限的, 或者不对所有设备适配的
    # 可能无法执行, 因此默认关闭了, 未来可以考虑启动这些命令
    elif self.客户端 == "RemoteAndroid":
      # 热重启系统，需要root权限
      command.append([self.adb_path, "-s", self.LINKURL, "shell", "stop"])
      command.append([self.adb_path, "-s", self.LINKURL, "shell", "start"])
      command.append([self.adb_path, "disconnect", self.LINKURL])
    elif self.客户端 == "USBAndroid":
      result = getPopen([self.adb_path, "devices"])
      if self.LINKURL in result[1]:
        command.append([self.adb_path, "-s", self.LINKURL, "reboot"])
      else:
        TimeECHO(f"没有找到USB设备{self.LINKURL}\n"+result[1])
        command = []
    elif self.客户端 == "FULL_ADB":
      # 通过reboot的方式可以实现重启和解决资源的效果
      command.append([self.adb_path, "connect", self.LINKURL])
      command.append([self.adb_path, "-s", self.LINKURL, "reboot"])
    #
    # 开始运行
    exit_code = run_command(command=command, sleeptime=60)
    if exit_code == 0:
      TimeECHO(f"关闭成功")
      return True
    else:
      TimeECHO(f"关闭失败")
      return False

  def 重启设备(self, sleeptime=0):
    TimeECHO(f"重新启动({self.LINK})")
    self.关闭设备()
    sleeptime = max(10, sleeptime-60)
    printtime = max(30, sleeptime/10)
    TimeECHO("sleep %d min" % (sleeptime/60))
    for i in np.arange(int(sleeptime/printtime)):
      TimeECHO(f"...taskkill_sleep: {i}", end='\r')
      sleep(printtime)
    self.启动设备()

  def 重启重连设备(self, sleeptime=0):
    self.重启设备(sleeptime=sleeptime)
    return self.连接设备()

  def 解锁设备(self, sleeptime=0):
    success = not self.device.is_locked()
    if not success:
      TimeECHO("屏幕已锁定")
      # 先唤醒
      self.device.wake()
      # 再向上滑动解锁
      if not "width" in self.display_info.keys():
        self.设备信息()
      w = self.display_info["width"]
      h = self.display_info["height"]
      swipe((w/2, h*0.9), (w/2, h*0.5))
      # 尝试用airtest的解锁. 很容易失败
      if self.device.is_locked():
        TimeECHO("滑动解锁失败")
        self.device.unlock()
        if self.device.is_locked():
          TimeECHO("unlock()方式解锁失败")
      #
      success = not self.device.is_locked()
      if success:
        TimeECHO(f"解锁成功")
    return success

  def 返回键(self):
    self.device.keyevent("BACK")
    TimeECHO(f"TOUCH.返回键")
    sleep(0.5)
    return

  def HOME键(self):
    self.device.keyevent("HOME")
    TimeECHO(f"TOUCH.HOMOE键")
    sleep(0.5)
    return
  #

  def shell(self, command=[], sleeptime=20,  quiet=False, must_ok=False):
    # 暂时仅支持android
    """
    adb shell ...
    """
    if not self.LINKtype == "android":
      TimeECHO(f"{fun_name(1)}暂不支持{self.LINKtype}")
      return -100
    #
    exit_code_o = 0
    command_step = 0
    # 获得运行的结果
    if not quiet:
      TimeECHO(funs_name())
    for i_command in command:
      if len(i_command) < 1:
        continue
      # 去掉所有的空白符号看是否还有剩余命令
      trim_insert = shlex.join(i_command).strip()
      if len(trim_insert) < 1:
        continue
      if not quiet:
        TimeECHO("  devices shell:"+trim_insert)
      try:
        result = [0, str(self.device.adb.shell(i_command))]
      except:
        result = [1, traceback.format_exc()]
      command_step = command_step + 1
      exit_code = result[0]
      if not quiet:
        if exit_code != 0:
          TimeECHO("result:"+">"*20)
          TimeECHO(result[1])
          TimeECHO("result:"+"<"*20)
      exit_code_o += exit_code
      if must_ok and exit_code_o != 0:
        break
      sleep(sleeptime)
    # 没有执行任何命令
    if command_step == 0:
      exit_code_o = -100
    return exit_code_o

  def 打开网址(self, url="https://cndaqiang.github.io"):
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
      TimeECHO(f"{fun_name(1)}暂不支持{self.LINKtype}")
      return -100
    return self.shell(command)

