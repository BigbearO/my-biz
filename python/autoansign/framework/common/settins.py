
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import logging
import os
import sys
# 临时目录,用于存放临时文件
import tempfile
# .......
from datetime import timezone, timedelta

# 重写函数#
from airtest.core.api import Template as Template_o

from framework.common.common_func import TimeECHO, loggerhead, TimeDebug
from framework.common.yaml_reader import readyaml


class Settings(object):
  #
  # 特色修改
  # python解释器是AirtestIDE还是终端的python
  AirtestIDE = "AirtestIDE" in sys.executable
  start_app_syskeys = False
  current_file_path = os.path.abspath(__file__)
  current_dir = os.path.dirname(current_file_path)
  testpng = Template_o(os.path.join(current_dir, "tpl_target_pos.png"), record_pos=(-0.28, 0.153), resolution=(960, 540), target_pos=6)
  #
  # 控制端
  platform = sys.platform.lower()
  # 避免和windows名字接近
  platform = "macos" if "darwin" in platform else platform
  #
  # control, 运行控制
  prefix = ""
  figdir = "assets"  # 输入的静态(图片)资源目录
  figdirs = [figdir]  # 默认的静态(图片)资源目录, 输入的优先级 figdir > "./" >  figdirs.append()
  # 时间参数
  # 防止服务器时区不同, 影响对游戏的执行时间判断
  # 设置为8则为东八区
  mobiletime = 8
  eastern_eight_offset = timedelta(hours=mobiletime)
  eastern_eight_tz = timezone(eastern_eight_offset)
  # 日志参数
  logger_dict = [logging.DEBUG, logging.INFO, logging.WARNING, logging.ERROR, logging.CRITICAL]
  logger_level = 1  # 设置为0输出详细模式
  logging.basicConfig(level=logging.INFO, format='%(message)s')
  logger = logging.getLogger("airtest_mobileauto")
  logger.setLevel(logging.DEBUG)
  outputnode = -1
  logfile = "result.0.txt"
  logfile_dict = {0: "result.0.txt"}
  #

  # client, 客户端情况
  mynode = 0
  totalnode = 1
  multiprocessing = False
  # 客户端
  # 虚拟机,android docker, iphone, etc,主要进行设备的连接和重启
  dockercontain = {}  # "androidcontain"
  BlueStackdir = ""  # "C:\Program Files\BlueStacks_nxt"
  LDPlayerdir = ""  # "D:\GreenSoft\LDPlayer"
  MuMudir = ""  # "D:\Program Files\Netease\MuMu Player 12\shell"
  # ADB地址
  LINK_dict = {0: "Android:///127.0.0.1:5555"}
  # 开始采用临时文件夹保存并行临时文件
  tmpdir = os.path.join(tempfile.gettempdir(), prefix)
  #
  # 注意Config新增的控制参数, 必须在init中进行定义
  win_Instance = {}  # 模拟器启动的编号
  win_InstanceName = {}  # 模拟器运行后的窗口名
  BossKeydict = {18: "alt", 17: "ctrl", 16: "shift", 88: "x", 81: "q"}
  BossKey = {0: []}  # 老板键,快速隐藏APP
  cmd_start_device = {}  # 自定义开启设备命令
  cmd_stop_device = {}  # 自定义关闭设备命令

  @classmethod
  def Config(cls, config_file="config.yaml"):
    if not os.path.exists(config_file):
      return
    # 不指定config的时候, 下面的就没法读入了
    if ".yaml" == config_file[-5:] or ".yml" == config_file[-4:]:
      config = readyaml(config_file)
    else:
      TimeECHO("airtest_mobileauto已采用yaml格式配置文件，请升级相关程序")
      exit()
    # node info
    cls.mynode = config.getint('mynode', cls.mynode)
    cls.totalnode = config.getint('totalnode', cls.totalnode)
    cls.multiprocessing = config.getboolean('multiprocessing', cls.multiprocessing) and cls.totalnode > 1

    # control
    cls.prefix = config.getstr('prefix', cls.prefix)
    cls.tmpdir = config.getstr('tmpdir', os.path.join(tempfile.gettempdir(), "airtest_mobileauto", cls.prefix))
    os.makedirs(cls.tmpdir, exist_ok=True)
    cls.figdir = config.get('figdir', cls.figdir)
    cls.figdirs = [cls.figdir]
    #
    cls.mobiletime = config.getint('mobiletime', cls.mobiletime)
    eastern_eight_offset = timedelta(hours=cls.mobiletime)
    cls.eastern_eight_tz = timezone(eastern_eight_offset)
    #
    cls.logger_level = config.getint('logger_level', cls.logger_level)
    level = Settings.logger_dict[Settings.logger_level]
    logging.basicConfig(level=level, format='%(message)s')
    logger = logging.getLogger("airtest_mobileauto")
    logger.setLevel(level)
    cls.outputnode = config.getint('outputnode', cls.outputnode)
    # 输入日志到文件
    for i in range(10):
      cls.logfile_dict[i] = ""
    cls.logfile_dict = config.get('logfile', cls.logfile_dict)
    # 处理旧日志
    for i in cls.logfile_dict.keys():
      if cls.multiprocessing:
        if i >= cls.totalnode:
          continue
      else:
        if i != cls.mynode:
          continue
      if os.path.exists(cls.logfile_dict[i]):
        try:
          os.remove(cls.logfile_dict[i]+".old.txt")
        except:
          pass
        try:
          os.rename(cls.logfile_dict[i], cls.logfile_dict[i]+".old.txt")
        except:
          pass
    #
    # client
    cls.dockercontain = config.get('dockercontain', cls.dockercontain)
    #
    cls.BlueStackdir = config.getstr('BlueStackdir', cls.BlueStackdir)
    cls.LDPlayerdir = config.getstr('LDPlayerdir', cls.LDPlayerdir)
    cls.MuMudir = config.getstr('MuMudir', cls.MuMudir)
    emulator = ""
    cls.win_Instance = {}  # 模拟器启动的编号
    cls.win_InstanceName = {}  # 模拟器运行后的窗口名
    #
    cls.BossKeydict = {18: "alt", 17: "ctrl", 16: "shift", 88: "x", 81: "q"}
    cls.BossKey = {}  # 老板键,快速隐藏APP
    # 关于Bosskey的说明
    # BlueStacks/LDPlayer多开的不同模拟器，快捷键是相同的
    # 快捷键会让前台变后台，后天变前台。
    # 因此：在启动新的模拟器之前，按下Bosskey把所有的模拟器都调到前台，启动后，再全部隐藏
    # MuMu多开时会快捷键会提示冲突,
    # 快捷键让所有的多开同时前台/后台
    # [旧版本方案] 通过给不同模拟器指定不同的快捷键: ctrl + alt + mynode, cls.BossKey[i] = [17, 18, 48+i] 并且交叉启动模拟器，可以解决这些问题
    # [新方案] 与BlueStack模拟器采用相同处理，快捷键前台-打开模拟器-快捷键后台. 即使存在快捷键冲突的提示，也可以成功隐藏模拟器
    # 新方案的好处是代码和BlueStacks/LDPlayer通用
    #
    if len(cls.BlueStackdir) > 0:
      cls.win_Instance[0] = "Nougat32"
      cls.win_InstanceName[0] = "BlueStacks App Player"
      for i in range(5):
        cls.win_Instance[i] = f"{cls.win_Instance[0]}_{i}"
        # BlueStacks没有提供终端开启关闭模拟器的方法，需要根据窗口名检索PID关闭模拟器
        cls.win_InstanceName[i] = f"{cls.win_InstanceName[0]} {i}"
        cls.BossKey[i] = [17, 16, 88]  # ctrl+shift+X
      emulator = 'BlueStack'
    elif len(cls.LDPlayerdir) > 0:
      for i in range(5):
        cls.win_Instance[i] = f"{i}"
        cls.BossKey[i] = [17, 81]  # ctrl+q
      emulator = 'LDPlayer'
    elif len(cls.MuMudir) > 0:
      for i in range(5):
        cls.win_Instance[i] = f"{i}"
        cls.BossKey[i] = [18, 81]  # alt+q
      emulator = 'MuMu'
    else:
      for i in range(10):
        cls.BossKey[i] = []
    if len(emulator) > 0:
      cls.win_Instance = config.get(emulator+'_Instance', cls.win_Instance)
      cls.win_InstanceName = config.get(emulator+'_Windows', cls.win_InstanceName)
      # 是否替换默认的BossKey
      BossKey = config.get('BossKey', cls.BossKey)
      cls.BossKey.update(BossKey)
    #
    cls.cmd_start_device = config.get('start_device', cls.cmd_start_device)
    cls.cmd_stop_device = config.get('stop_device', cls.cmd_stop_device)
    #
    # 读取LINK_dict，假设配置文件中存储的是字符串形式的字典
    # 本地docker容器
    cls.LINK_dict[0] = "Android:///127.0.0.1:5555"
    cls.LINK_dict[1] = "Android:///127.0.0.1:5565"
    cls.LINK_dict[2] = "Android:///127.0.0.1:5575"
    cls.LINK_dict[3] = "Android:///127.0.0.1:5585"
    cls.LINK_dict[4] = "Android:///127.0.0.1:5595"
    cls.LINK_dict[5] = "Android:///192.168.192.10:5555"  # 服务器上的docker容器
    cls.LINK_dict[6] = "Android:///192.168.192.10:5565"  # 服务器上的docker容器
    cls.LINK_dict[7] = "ios:///http://127.0.0.1:8200"  # Iphone SE映射到本地
    cls.LINK_dict[8] = "ios:///http://169.254.83.56:8100"  # Iphone 11支持无线连接
    cls.LINK_dict[9] = "Android:///emulator-5554"  # 本地的安卓模拟器
    cls.LINK_dict[10] = "Android:///4e86ac13"  # usb连接的安卓手机
    cls.LINK_dict = config.get('LINK_dict', cls.LINK_dict)
    #
    # 初始化之后，根据参数修改配置
    cls.logfile = cls.logfile_dict[cls.mynode]
  #

  @classmethod
  def Config_mynode(cls, mynode):
    cls.mynode = mynode
    cls.logfile = cls.logfile_dict[cls.mynode]

  @classmethod
  def info(cls, prefix=""):
    TimeDebug("mynode="+str(cls.mynode))
    TimeDebug("totalnode="+str(cls.totalnode))
    TimeDebug("LINK_dict="+str(cls.LINK_dict))
    TimeDebug("tmpdir="+str(cls.tmpdir))


