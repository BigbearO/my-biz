#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import logging
import subprocess
import traceback

# 临时目录,用于存放临时文件
# 重写函数#
from airtest.core.api import sleep, uninstall
from airtest.core.settings import Settings as ST

# ........................
# python -m pip install --upgrade --no-deps --force-reinstall airtest
# vscode设置image preview的解析目录为assets,就可以预览了
ST.OPDELAY = 1
# 全局阈值的范围为[0, 1]
ST.THRESHOLD_STRICT = 0.8
ST.THRESHOLD = 0.8  # 其他语句的默认阈值
# ST.FIND_TIMEOUT=10 #*2 #获取截图的时间限制
# ST.FIND_TIMEOUT_TMP=1#匹配图形的时间限制, 也许可以再改小些加速

# 控制屏幕输出
# 这个设置可以极低的降低airtest输出到屏幕的信息
# 但是在关闭设备后(airtest无法工作)，就不可以在用logger()输出信息了，会出错
logger = logging.getLogger("airtest")
logger.setLevel(logging.WARNING)

def fun_name(level=1):
  """
  def b()
      fun_name(1) == "b"
  """
  import inspect
  fun = inspect.currentframe()
  ilevel = 0
  for i in range(level):
    try:
      fun = fun.f_back
      ilevel = ilevel+1
    except:
      break
  try:
    return str(fun.f_code.co_name)
  except:
    return ""


def funs_name(level=2):
  """
  2 就是调用funs_name的函数
  """
  i = level
  content = fun_name(i)
  while i < 10:
    i = i+1
    i_name = fun_name(i)
    if len(i_name) > 0:
      content = content + "." + i_name
    else:
      break
  return content

# 如果命令需要等待打开的程序关闭, 这个命令很容易卡住
# 不同subprocess.getstatusoutput的语法规则有差异
# linux可以，subprocess.getstatusoutput("adb version"),subprocess.getstatusoutput(["adb","version"])就报错
# 而windows刚好反过来
# 该函数已被getPopen替代
def getstatusoutput(*args, **kwargs):
  try:
    return subprocess.getstatusoutput(*args, **kwargs)
  except:
    return [1, traceback.format_exc()]
#


def getPopen(command):
  try:
    # shell用于支持$(cat )等命令, 并且只能用一个字符串
    shell = len(command) == 1
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=shell)
    sleep(2)  # 等待一下才有结果
    # 这个命令有机会卡住，比如初次启动Bluestack时
    try:
      stdout, stderr = process.communicate(timeout=10)
    except:
      stdout = f"执行{command}超时"
      stderr = ""
    #
    result = [len(stderr) > 0, str(stdout)+str(stderr)]
  except:
    result = [1, traceback.format_exc()]
  return result





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

#检测app是否安装
def existsapp(app):
  dev = device()
  try:
    if dev.check_app(app):
      return True
    else:
      return False
  except Exception as e:
    print(e)
    print('检测异常安装')
    return False

#安装pak包
def installapk(path ="apk path"):
  try:
    if existsapp(app) == True:
      print(existsapp(app),'app存在，卸载安装')#app为app包名，如：com.tencent.qqlivehuawei
      uninstall(app)
      install(path)
    else:
      print('app不存在，安装pak')
      install(path)
  except Exception as e:
    print(e)
    print('检测异常安装')
    install(path)
  if existsapp(app) == True:
    print('安装成功')
  else:
    print('安装失败')
