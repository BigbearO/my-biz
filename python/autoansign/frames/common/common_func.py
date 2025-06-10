#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import shlex
import subprocess
import sys
import traceback

import numpy as np
import yaml
from airtest.core.api import Template as Template_o
from airtest.core.api import exists as exists_o
# 重写函数#
from airtest.core.api import sleep
from airtest.core.api import swipe as swipe_o
from airtest.core.api import touch as touch_o
from airtest.core.settings import Settings as ST

from frames.common.auto_constants import AutoConstants
from frames.common.base_func import funs_name, fun_name, getPopen
from frames.common.property import AutoProperty

logInfo = AutoProperty.logInfo


# ........................
# python -m pip install --upgrade --no-deps --force-reinstall airtest
# vscode设置image preview的解析目录为assets,就可以预览了
ST.OPDELAY = 1
# 全局阈值的范围为[0, 1]
ST.THRESHOLD_STRICT = 0.8
ST.THRESHOLD = 0.8  # 其他语句的默认阈值
# ST.FIND_TIMEOUT=10 #*2 #获取截图的时间限制
# ST.FIND_TIMEOUT_TMP=1#匹配图形的时间限制, 也许可以再改小些加速



def save_yaml(vsr_dict={}, yamlfile=""):
  try:
    # 写入 YAML 文件
    with open(yamlfile, 'w', encoding='utf-8') as f:
      yaml.dump(vsr_dict, f, allow_unicode=True)
    logInfo.TimeECHO(f"保存 [{yamlfile}]")
    return True
  except:
    traceback.format_exc()
    logInfo.TimeErr(f"{funs_name()}写入yaml文件{yamlfile}失败")
    return False

def Template(*args, **kwargs):
  # 在这里修改args和kwargs，例如针对kwargs中的key进行添加内容
  dirname = []
  if "dirname" in kwargs:
    dirname.append(kwargs["dirname"])
    del kwargs["dirname"]
  dirname.extend(AutoConstants.figdirs)
  dirname.append("./")
  args_list = list(args)
  if args_list and "png" in args_list[0]:
    for dir in dirname:
      filename = os.path.join(dir, args_list[0].lstrip('/'))
      if os.path.exists(filename):
        args_list[0] = os.path.join(dir, args_list[0].lstrip('/'))
        break
    else:
      logInfo.TimeErr(f"不存在{args_list[0]}")
    args = args_list
  # 调用Template_o函数，传入修改后的参数
  return Template_o(*args, **kwargs)

# 替代基础的print函数



def run_command(command=[], sleeptime=20,  quiet=False, must_ok=False):
  """
   执行命令
   统一采用subprocess.Popen(["exec","para","para2","..."])
  """
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
      logInfo.TimeECHO("  sysrun:"+trim_insert)
    #
    try:
      # os.system的容易卡，各种命令兼容性也不好，subprocess.Popen可以直接填windows快捷方式里的内容
      result = getPopen(i_command)
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


def run_class_command(self=None, command=[], quiet=False, must_ok=False):
  """
# 执行模块内的文件
# 以为文件中的命令可能包含self,所以把self作为输入参数
  """
  # 获得运行的结果
  exit_code_o = 0
  command_step = 0
  if not quiet:
    logInfo.TimeECHO(funs_name())
  for i_command in command:
    # 去掉所有的空白符号看是否还有剩余命令
    trim_insert = i_command.strip()
    if len(trim_insert) < 1:
      continue
    if '#' == trim_insert[0]:
      continue
    if not quiet:
      logInfo.TimeECHO("  python: "+i_command.rstrip())
    try:
      exec(i_command)
      exit_code = 0
      command_step = command_step + 1
    except:
      traceback.print_exc()
      exit_code = 1
    exit_code_o += exit_code
    if must_ok and exit_code_o != 0:
      break
  # 没有执行任何命令
  if command_step == 0:
    exit_code_o = -100
  return exit_code_o


def getpid_win(IMAGENAME="HD-Player.exe", key="BlueStacks App Player 0"):
  if sys.platform.lower() != "win32":
    return 0
  try:
    command = ["tasklist", "-FI", f"IMAGENAME eq {IMAGENAME}", "/V"]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, shell=True)
    sleep(5)  # 等待一下才有结果
    output, _ = process.communicate()
    # 中文的windows系统默认返回gbk的编码
    # 尝试使用不同编码解码输出
    encodings = ['gbk', 'utf-8']  # 可以添加其他编码
    decoded_output = None
    for encoding in encodings:
      try:
        decoded_output = output.decode(encoding)
        break
      except UnicodeDecodeError:
        continue
    if not decoded_output:
      logInfo.TimeECHO(f"getpid_win({IMAGENAME}) error"+":无法解析输出")
      return 0
    cont = decoded_output.splitlines()
    cont = output.decode('gbk', errors='ignore').splitlines()
  except:
    logInfo.TimeECHO(f"getpid_win({IMAGENAME}) error"+"-"*10)
    traceback.print_exc()
    logInfo.TimeECHO(f"getpid_win({IMAGENAME}) error"+"-"*10)
    return 0
  PID = 0
  logInfo.TimeECHO(f"{fun_name(1)}.from {cont}")
  for task in cont:
    taskterm = task.split()
    if len(taskterm) < 3:
      continue
    # IMAGENAME如果太长了会显示不全，因此不能直接IMAGENAME in task
    lenname = len(taskterm[0])
    if lenname == 0:
      continue
    if lenname < len(IMAGENAME):
      if not taskterm[0] == IMAGENAME[:lenname]:
        continue
    # key还是可以显示全的
    if key in task:
      PID = task.split()[1]
      try:
        logInfo.TimeECHO(f"getpid_win:{task}")
        PID = int(PID)
      except:
        logInfo.TimeECHO(f"getpid_win({IMAGENAME},{key}) error"+"-"*10)
        traceback.print_exc()
        logInfo.TimeECHO(f"getpid_win({IMAGENAME},{key}) error"+"-"*10)
        PID = 0
      break
  return PID


def touchkey_win(Key=[]):
  if len(Key) == 0:
    return
  if AutoConstants.platform != "win32":
    logInfo.TimeECHO(f"平台{AutoConstants.platform} not support {fun_name(1)}")
    return False
  try:
    import win32api
    import win32con
  except:
    traceback.print_exc()
    return False
  #
  touchkey = ""
  for ikey in Key:
    if ikey in AutoConstants.BossKeydict.keys():
      touchkey = touchkey+f" {AutoConstants.BossKeydict[ikey]}"
    else:
      touchkey = touchkey+" unknow"
  logInfo.TimeECHO(f"{fun_name(1)}: {touchkey}")
  #
  for ikey in Key:
    win32api.keybd_event(ikey, 0, 0, 0)
  for ikey in Key[::-1]:
    win32api.keybd_event(ikey, 0, win32con.KEYEVENTF_KEYUP, 0)
  sleep(5)

# todo 优化一下看能不能直接用deviceOB
def connect_status(times=10):
  for i in np.arange(times):
    try:
      exists_o(AutoConstants.testpng)
      return True
    except:
      if i == times - 1:
        traceback.print_exc()
      logInfo.TimeECHO(f"{funs_name()}无法连接设备,重试中{i}")
      sleep(1)
      continue
  logInfo.TimeECHO(f"设备失去联系")
  return False
# ........................


def exists(*args, **kwargs):
  try:
    result = exists_o(*args, **kwargs)
  except:
    result = False
    logInfo.TimeECHO(f" {fun_name(1)}  失败")
    if not connect_status():
      logInfo.TimeErr(f"{fun_name(1)}连接不上设备")
      return result
    sleep(1)
    try:
      result = exists_o(*args, **kwargs)
    except:
      traceback.print_exc()
      logInfo.TimeECHO(f"再次尝试{fun_name(1)}仍失败")
      result = False
  return result


def touch(*args, **kwargs):
  try:
    result = touch_o(*args, **kwargs)
  except:
    result = False
    logInfo.TimeECHO(f" {fun_name(1)}  失败")
    if not connect_status():
      logInfo.TimeErr(f"{fun_name(1)}连接不上设备")
      return result
    sleep(1)
    try:
      result = touch_o(*args, **kwargs)
    except:
      traceback.print_exc()
      logInfo.TimeECHO(f"再次尝试{fun_name(1)}仍失败")
      result = False
  return result


def swipe(*args, **kwargs):
  result = False
  try:
    result = swipe_o(*args, **kwargs)
  except:
    result = False
    logInfo.TimeECHO(f" {fun_name(1)}  失败")
    if not connect_status():
      logInfo.TimeErr(f"{fun_name(1)}连接不上设备")
      return result
    sleep(1)
    try:
      result = swipe_o(*args, **kwargs)
    except:
      traceback.print_exc()
      logInfo.TimeECHO(f"再次尝试{fun_name(1)}仍失败")
      result = False
  return result
