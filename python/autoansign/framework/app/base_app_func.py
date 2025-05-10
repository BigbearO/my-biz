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


def start_app(*args, **kwargs):
  if Settings.start_app_syskeys:
    args_list = list(args)
    args_list[0] = str(args_list[0])+" --pct-syskeys 0"
    args = args_list
    TimeECHO(f"{fun_name(1)} with {args_list[0]}")
    Settings.start_app_syskeys = True
  try:
    result = True
    start_app_o(*args, **kwargs)
  except:
    result = False
    TimeECHO(f"{fun_name(1)} 失败")
    if not connect_status():
      TimeErr(f"{fun_name(1)}连接不上设备")
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
      虽然start_app(self.APPID)也能启动, 但是要修改代码airtest/core/android/adb.py,
      即使用start_app(self.APPID,Activity)就不用修改代码了
      """
      args_list = list(args)
      if args_list and "SYS_KEYS has no physical keys but with factor" in errormessgae:
        args_list = list(args)
        args_list[0] = str(args_list[0])+" --pct-syskeys 0"
        args = args_list
        TimeECHO(f"{fun_name(1)} with {args_list[0]}")
        Settings.start_app_syskeys = True
      if "device offline" in errormessgae:
        TimeECHO("ADB device offline")
        return result
    # ......
    try:
      result = True
      start_app_o(*args, **kwargs)
    except:
      traceback.print_exc()
      TimeECHO(f"再次尝试{fun_name(1)}仍失败，检测是否没有开启ADB,或者重新启动ADB")
      result = False
  return result



def stop_app(*args, **kwargs):
  try:
    result = True
    stop_app_o(*args, **kwargs)
  except:
    result = False
    TimeECHO(f"{fun_name(1)} 失败")
    if not connect_status():
      TimeErr(f"{fun_name(1)}连接不上设备")
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
      TimeECHO(f"再次尝试{fun_name(1)}仍失败")
      result = False
  return result
