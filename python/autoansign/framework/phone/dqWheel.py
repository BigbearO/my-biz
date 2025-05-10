
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

class DQWheel:
  def __init__(self, var_dict_file='var_dict_file.yaml', mynode=-10, totalnode=-10):
    self.timedict = {}
    self.mynode = mynode
    self.totalnode = totalnode
    self.totalnode_bak = totalnode
    #
    self.barrierlimit = 60*20  # 同步最大时长
    self.filelist = []  # 建立的所有文件，用于后期clear
    self.var_dict_file = var_dict_file
    self.var_dict = self.read_dict(self.var_dict_file)
    self.savepos = True
    # 子程序运行次数
    self.calltimes_dict = {}
    # 所有的临时文件, 即.tmp.开头的文件, 都存储到Settings.tmpdir目录
    os.makedirs(Settings.tmpdir, exist_ok=True)
    self.stopfile = os.path.join(Settings.tmpdir, ".tmp.barrier.EXIT.txt")
    self.stopinfo = ""
    self.connecttimes = 0
    self.connecttimesMAX = 20
    # 同步文件放在运行目录, 也方便调试
    self.辅助同步文件 = f"NeedRebarrier.txt"
    self.独立同步文件 = f"NeedRebarrier.{self.mynode}.{self.totalnode}.txt"
    self.removefile(self.独立同步文件)
    self.removefile(self.stopfile)

  def list_files(self, path):
    files = []
    with os.scandir(path) as entries:
      for entry in entries:
        files.append(entry.name)
    return files
  #

  def init_clean(self):
    # 多节点时，要同步成功后，采用主节点进行删除,不然时间差会导致很多问题
    self.removefiles(dir=Settings.tmpdir, head=".tmp.barrier.", foot=".txt")
  #

  def timelimit(self, timekey="", limit=0, init=True, reset=True):
    if len(timekey) == 0:
      timekey = "none"
    if not timekey in self.timedict.keys():
      init = True
    if init:
      self.timedict[timekey] = time.time()
      self.timedict[timekey+".reset"] = time.time()
      return False
    else:
      # reset 是大时间范围内的子时间循环循环,例如在整个对战期间,每隔5分钟点一下屏幕
      if not reset:
        if time.time()-self.timedict[timekey+".reset"] > limit:
          self.timedict[timekey+".reset"] = time.time()
          return True
        else:
          return False
      elif time.time()-self.timedict[timekey] > limit:
        self.timedict[timekey] = time.time()
        return True
      else:
        return False

  def removefile(self, filename, quiet=True):
    if os.path.exists(filename):
      try:
        os.remove(filename)
        TimeECHO("删除 ["+filename+"] 成功")
      except:
        traceback.print_exc()
        TimeECHO("删除 ["+filename+"] 失败")
        return False
      if os.path.exists(filename):
        TimeErr("删除 ["+filename+"] 失败, 还存在")
        return False
      else:
        return True
    else:
      if not quiet:
        TimeECHO("不存在["+filename+"]")
      return False

  def removefiles(self, dir=".", head="", body="", foot="", quiet=True):
    l_head = len(head)
    l_body = len(body)
    l_foot = len(foot)
    if l_head+l_body+l_foot == 0:
      return True
    for name in os.listdir(dir):
      isname = True
      if len(name) < max(l_head, l_body, l_foot):
        continue
      # 必须三个条件都满足才能删除
      if l_head > 0:
        if not head == name[:l_head]:
          continue
      if l_body > 0:
        if not body in name:
          continue
      if l_foot > 0:
        if not foot == name[-l_foot:]:
          continue
      #
      if isname:
        self.removefile(os.path.join(dir, name), quiet)
    return True

  def touchfile(self, filename, content=""):
    """
    只要content有内容，就重新建立
    """
    if os.path.exists(filename):
      self.removefile(filename)
    #
    content = str(content)
    TimeECHO(f"创建 [{filename}].[{content}]")
    with open(filename, 'w', encoding='utf-8') as f:
      f.write(content)
    return True

  def touchstopfile(self, content="stop"):
    self.touchfile(self.stopfile, content=content)
    self.stopinfo = content

  def stopnow(self):
    return os.path.exists(self.stopfile)

  def readstopfile(self):
    if self.stopnow:
      return self.readfile(self.stopfile)[0]
    else:
      return ""

  def readfile(self, filename):
    if not os.path.exists(filename):
      TimeECHO("不存在 ["+filename+"]")
      return [""]
    try:
      f = open(filename, 'r', encoding='utf-8')
      content = f.readlines()
      f.close()
      content = content if len(content) > 0 else [""]
      TimeECHO("读取 ["+filename+"] 成功")
      return content
    except:
      traceback.print_exc()
      TimeECHO("读取 ["+filename+"] 失败")
      return [""]

  #
  def touch同步文件(self, 同步文件="", content=""):
    """
    content有内容，文件已经存在了也不会重建
    """
    if len(同步文件) > 1:
      同步文件 = 同步文件
    else:
      同步文件 = self.辅助同步文件 if self.totalnode_bak > 1 else self.独立同步文件
    #
    if 同步文件 == self.辅助同步文件:
      self.touch同步文件(self.独立同步文件, content)
    #
    if self.存在同步文件(同步文件):
      TimeECHO(f"不再创建 [{同步文件}]")
      return True
    content = loggerhead()+funs_name() if len(content) == 0 else content
    TimeECHO(f"**** TOUCH **** 创建同步文件 [{同步文件}]")
    self.touchfile(同步文件, content)
    #
    return True

  def 存在同步文件(self, 同步文件=""):
    if len(同步文件) > 1:
      if os.path.exists(同步文件):
        TimeECHO(f"存在同步文件 [{同步文件}]")
        return True
      else:
        return False
    # 只要是总结点数大于1,无论当前是否组队都判断辅助同步文件
    if self.totalnode_bak > 1 and os.path.exists(self.辅助同步文件):
      TimeECHO(f"存在辅助同步文件 [{self.辅助同步文件}]")
      return True
    # 每个进程的独立文件不同,不同节点不会误判
    if os.path.exists(self.独立同步文件):
      TimeECHO(f"存在独立同步文件 [{self.独立同步文件}]")
      return True
    return False

  def clean文件(self):
    for i in self.filelist:
      if os.path.exists(i):
        self.removefile(i)
    self.filelist = []
  #

  def barriernode(self, mynode, totalnode, name="barrierFile"):
    if totalnode < 2:
      return True
    if self.存在同步文件():
      TimeErr(f"同步{name}.检测到同步文件")
      return True
    filelist = []
    ionode = mynode == 0 or totalnode == 1
    #
    if ionode:
      TimeECHO(f"BARRIERNODE [{name}]")
    #
    for i in np.arange(1, totalnode):
      filename = f".tmp.barrier.{i}.{name}.txt"
      filename = os.path.join(Settings.tmpdir, filename)
      if ionode:
        if os.path.exists(filename):
          TimeErr("完蛋,barriernode之前就存在同步文件")
        self.touchfile(filename)
      filelist.append(filename)
      self.filelist.append(filename)
    #
    self.timelimit(timekey=name, limit=self.barrierlimit, init=True)
    times = 0
    while not self.timelimit(timekey=name, limit=self.barrierlimit, init=False):
      times = times+1
      if self.存在同步文件():
        return True
      if ionode:
        barrieryes = True
        for i in filelist:
          barrieryes = barrieryes and not os.path.exists(i)
          if not barrieryes:
            break
        if barrieryes:
          TimeECHO(f"BARRIERNODE [{name}] 同步完成")
          TimeECHO("."*10)
          return True
        if times % 15 == 0:
          TimeECHO(f"BARRIERNODE [{name}] 同步检测...")
      else:
        if self.removefile(filelist[mynode-1]):
          return True
      sleep(2)
    if ionode:
      for i in filelist:
        self.removefile(i)
      # 不清除也没事,start时会自动清除
    TimeErr(f"BARRIERNODE [{name}] 同步失败,创建同步文件")
    self.touch同步文件()
    return False

  def read_dict(self, var_dict_file="position_dict.yaml"):
    var_dict = {}
    TimeECHO(f"读取 [{var_dict_file}]")
    if not os.path.exists(var_dict_file):
      TimeECHO("不存在 "+var_dict_file)
      return var_dict
    # 读取变量
    # read_dict 不仅适合保存字典,而且适合任意的变量类型
    if ".yaml" == var_dict_file[-5:] or ".yml" == var_dict_file[-4:]:
      # 读取 YAML 文件
      config = readyaml(var_dict_file)
      return config.yaml_dict
    #
    TimeECHO(f"请升级程序，使用yaml格式存储字典")
    # 旧版本的pickle格式, 适合存储任意数据
    import pickle
    try:
      with open(var_dict_file, 'rb') as f:
        var_dict = pickle.load(f)
    except:
      traceback.print_exc()
      TimeECHO("文件为空，已设置 var_dict 为None")
      var_dict = None  # 或者其他默认值
    return var_dict

  def save_dict(self, var_dict, var_dict_file="position_dict.yaml"):
    # 保存变量
    # save_dict 不仅适合保存字典,而且适合任意的变量类型
    if ".yaml" == var_dict_file[-5:]:
      if save_yaml(var_dict, var_dict_file):
        return
    #
    # 旧版本的pickle格式, 适合存储任意数据
    import pickle
    f = open(var_dict_file, "wb")
    pickle.dump(var_dict, f)
    f.close()

  def are_same_type(self, var1, var2):
    return type(var1) == type(var2)

  def bcastvar(self, mynode, totalnode, var, name="bcastvar"):
    # bcastvar 不仅适合保存字典,而且适合任意的变量类型
    if totalnode < 2:
      return var
    dict_file = ".tmp."+name+".txt"
    dict_file = os.path.join(Settings.tmpdir, dict_file)
    if mynode == 0:
      self.save_dict(var, dict_file)
    self.barriernode(mynode, totalnode, "bcastvar.S."+name)
    if self.存在同步文件():
      return var
    #
    var_new = self.read_dict(dict_file)
    if not self.are_same_type(var_new, var):
      self.touch同步文件(content=f"{fun_name(1)}.{name}.读取的变量类型不同于输入变量")
      var_new = var
    #
    self.barriernode(mynode, totalnode, "bcastvar.E."+name)
    if mynode == 0:
      self.removefile(dict_file)
    #
    return var_new

  def gathervar(self, mynode, totalnode, var, name="gathervar"):
    # gathervar 不仅适合保存字典,而且适合任意的变量类型
    if totalnode < 2:
      return [var]
    mydict_file = f".tmp.{name}.{mynode}.{totalnode}.txt"
    self.save_dict(var, mydict_file)
    self.barriernode(mynode, totalnode, "gathervar.S."+name)
    if self.存在同步文件():
      self.removefile(mydict_file)
      return [var]
    #
    vars = []
    for i in range(totalnode):
      dict_file = f".tmp.{name}.{i}.{totalnode}.txt"
      if not os.path.exists(dict_file):
        sleep(5)
        if not os.path.exists(dict_file):
          self.touch同步文件(同步文件=self.辅助同步文件, content=f"gathervar.{name}.找不到{dict_file}")
          self.removefile(mydict_file)
          return [var]
      var_new = self.read_dict(dict_file)
      if not self.are_same_type(var_new, var):
        self.touch同步文件(content=f"{fun_name(1)}.{name}.{i}.读取的变量类型不同于输入变量")
        var_new = var
      vars.append(var_new)
    #
    self.barriernode(mynode, totalnode, "gathervar.E."+name)
    self.removefile(mydict_file)
    if self.存在同步文件():
      self.removefile(mydict_file)
      return [var]
    #
    TimeECHO("gather["+name+"]成功")
    return vars

  def uniq_Template_array(self, arr):
    if not arr:  # 如果输入的列表为空
      return []
    #
    seen = set()
    unique_elements = []
    for item in arr:
      if item.filepath not in seen:
        unique_elements.append(item)
        seen.add(item.filepath)
    return unique_elements

  def 存在任一张图(self, array, strinfo="", savepos=False):
    array = self.uniq_Template_array(array)
    判断元素集合 = array
    strinfo = strinfo if len(strinfo) > 0 else "图片"
    if strinfo in self.calltimes_dict.keys():
      self.calltimes_dict[strinfo] = self.calltimes_dict[strinfo]+1
    else:
      self.calltimes_dict[strinfo] = 1
    content = f"第[{self.calltimes_dict[strinfo]}]次寻找{strinfo}"
    length = len(判断元素集合)
    for idx, i in enumerate(判断元素集合):
      TimeECHO(f"{content}({idx+1}/{length}):{i}")
      pos = exists(i)
      if pos:
        TimeECHO(f"{strinfo}成功:{i}")
        # 交换元素位置
        判断元素集合[0], 判断元素集合[idx] = 判断元素集合[idx], 判断元素集合[0]
        if savepos:
          self.var_dict[strinfo] = pos
          self.save_dict(self.var_dict, self.var_dict_file)
        return True, 判断元素集合
    return False, 判断元素集合

  def existsTHENtouch(self, png=Settings.testpng, keystr="", savepos=False):
    savepos = savepos and len(keystr) > 0 and self.savepos
    #
    if self.connecttimes > self.connecttimesMAX:  # 大概率连接失败了,判断一下
      if connect_status(times=max(2, self.connecttimesMAX-self.connecttimes+10)):  # 出错后降低判断的次数
        self.connecttimes = 0
      else:
        self.connecttimes = self.connecttimes+1
        self.touch同步文件(self.独立同步文件)
        return False
    #
    if savepos:
      if keystr in self.var_dict.keys():
        # 判断数据类型是否正确
        if not isinstance(self.var_dict[keystr], (tuple, list)):
          TimeECHO("该位置记录错误,正在删除字典: "+keystr)
          del self.var_dict[keystr]
        else:
          touch(self.var_dict[keystr])
          TimeECHO("touch (saved) "+keystr)
          sleep(0.1)
          return True
    pos = exists(png)
    if pos:
      self.connecttimes = 0
      touch(pos)
      if len(keystr) > 0:
        TimeECHO("touch "+keystr)
      if savepos:
        self.var_dict[keystr] = pos
        self.save_dict(self.var_dict, self.var_dict_file)
      return True
    else:
      self.connecttimes = self.connecttimes+1
      if len(keystr) > 0:
        TimeECHO("NotFound "+keystr)
      return False

  def cal_record_pos(self, record_pos=(0.5, 0.5), resolution=(960, 540), keystr="", savepos=False):
    x = 0.5*resolution[0]+record_pos[0]*resolution[0]
    y = 0.5*resolution[1]+record_pos[1]*resolution[0]
    pos = (int(x), int(y))
    if savepos and len(keystr) > 0:
      self.var_dict[keystr] = pos
      self.save_dict(self.var_dict, self.var_dict_file)
    return pos

  def touch_record_pos(self, record_pos=(0.5, 0.5), resolution=(960, 540), keystr="", savepos=False):
    pos = self.cal_record_pos(record_pos=record_pos, resolution=resolution, keystr=keystr, savepos=savepos)
    TimeECHO("touch (record_pos) "+keystr+f"{pos}")
    return touch(pos)

  #
  # touch的总时长timelimit s, 或者总循环次数<10
  def LoopTouch(self, png=Settings.testpng, keystr="", limit=0, loop=10, savepos=False):
    timekey = "LOOPTOUCH"+keystr+str(random.randint(1, 500))
    if limit + loop < 0.5:
      limit = 0
      loop = 1
    self.timelimit(timekey=timekey, limit=limit, init=True)
    runloop = 1
    while self.existsTHENtouch(png=png, keystr=keystr+f".{runloop}", savepos=savepos):
      if limit > 0:
        if self.timelimit(timekey=timekey, limit=limit, init=False):
          TimeErr("TOUCH"+keystr+"超时.....")
          break
      if runloop > loop:
        TimeErr("TOUCH"+keystr+"超LOOP.....")
        break
      sleep(10)
      runloop = runloop+1
    #
    if exists(png):
      TimeErr(keystr+"图片仍存在")
      return True
    else:
      return False
  # 这仅针对辅助模式,因此同步文件取self.辅助同步文件

  def 必须同步等待成功(self, mynode, totalnode, 同步文件="", 不再同步="", sleeptime=60*5):
    同步文件 = 同步文件 if len(同步文件) > 1 else self.辅助同步文件
    if totalnode < 2:
      self.removefile(同步文件)
      return True
    if self.存在同步文件(同步文件):  # 单进程各种原因出错时,多进程无法同步时
      if self.stopnow():
        return
      if os.path.exists(不再同步):
        TimeErr(f"检测到文件: [{不再同步}],结束{fun_name(1)}循环")
        return
      TimeECHO(f"---{mynode}---"*5)
      TimeECHO(f"存在同步文件 [{同步文件}],第一次尝试同步程序")
      start_timestamp = int(time.time())
      # 第一次尝试同步
      self.同步等待(mynode, totalnode, 同步文件, sleeptime)
      # 如果还存在说明同步等待失败,那么改成hh:waitminu*N时刻进行同步
      while self.存在同步文件(同步文件):
        if self.stopnow():
          return
        if os.path.exists(不再同步):
          TimeErr(f"检测到文件:{不再同步},结束{fun_name(1)}循环")
          return
        waitminu = int(min(59, 5*totalnode))
        TimeErr(f"仍然存在同步文件,进行{waitminu}分钟一次的循环")
        hour, minu, sec = self.time_getHMS()
        minu = minu % waitminu
        if minu > totalnode:
          sleepsec = (waitminu-minu)*60-sec
          TimeECHO(f"等待{sleepsec}s")
          sleep(sleepsec)
          continue
        end_timestamp = int(time.time())
        sleepNtime = max(10, sleeptime-(end_timestamp-start_timestamp))+mynode*5
        self.同步等待(mynode, totalnode, 同步文件, sleepNtime)
      TimeECHO(f"+++{mynode}+++"*5)
    else:
      return True
    return not self.存在同步文件(同步文件)

  # 这仅针对辅助模式,因此同步文件取self.辅助同步文件
  def 同步等待(self, mynode, totalnode, 同步文件="", sleeptime=60*5):
    # 同步等待是为了处理,程序因为各种原因无法同步,程序出粗.
    # 重新校验各个进程
    # Step1. 检测到主文件{同步文件} 进入同步状态
    # Step2. 确定所有进程均检测到主文件状态
    # Step3. 检测其余进程是否都结束休息状态
    # 一个节点、一个节点的check
    #
    同步文件 = 同步文件 if len(同步文件) > 1 else self.辅助同步文件
    if 同步文件 == self.辅助同步文件:
      self.removefile(self.独立同步文件)
    ionode = mynode == 0 or totalnode == 1
    if totalnode < 2:
      self.removefile(同步文件)
      return True
    if not os.path.exists(同步文件):
      return True
    #
    TimeECHO("进入同步等待")
    同步成功 = True
    name = 同步文件
    全部通信成功文件 = os.path.join(Settings.tmpdir, os.path.basename(同步文件)+".同步完成.txt")
    全部通信失败文件 = os.path.join(Settings.tmpdir, os.path.basename(同步文件)+".同步失败.txt")
    self.filelist.append(全部通信成功文件)
    # 前两个节点首先进行判定,因此先进行删除
    if mynode < 2:
      self.removefile(全部通信失败文件)
    # 最后一个通过才会删除成功文件,避免残留文件干扰
    self.removefile(全部通信成功文件)
    for i in np.arange(1, totalnode):
      if mynode > 0 and mynode != i:
        continue
      TimeECHO(f"进行同步循环{i}")
      sleep(mynode*1)
      #
      主辅通信成功 = False
      filename = f".tmp.barrier.{i}.{name}.in.txt"
      filename = os.path.join(Settings.tmpdir, filename)
      # 将随机校验数字，存储到 filename 中
      # 利用lockfile="xxx.随机校验数字.xxx"进行校验
      if ionode:
        hour, minu, sec = self.time_getHMS()
        myrandom = f"{i}{totalnode}{hour}{minu}{sec}"
        self.touchfile(filename, content=myrandom)
        lockfile = f".tmp.barrier.{myrandom}.{i}.{name}.in.txt"
        lockfile = os.path.join(Settings.tmpdir, lockfile)
        self.touchfile(lockfile)
        sleep(1)
        self.filelist.append(filename)
        self.filelist.append(lockfile)
        # 开始通信循环
        主辅通信成功 = False
        for sleeploop in np.arange(60*5):
          if not os.path.exists(lockfile):
            主辅通信成功 = True
            self.removefile(filename)
            break
          sleep(1)
        # 判断通信成功与否
        同步成功 = 同步成功 and 主辅通信成功
        if 同步成功:
          TimeECHO(f"同步{i}成功")
        else:
          TimeECHO(f"同步{i}失败")
          self.touchfile(全部通信失败文件)
          break
        continue
      else:
        同步成功 = False
        # 辅助节点,找到特定,就循环5分钟
        myrandom = ""
        lockfile = f".tmp.barrier.{myrandom}.{i}.{name}.in.txt"
        lockfile = os.path.join(Settings.tmpdir, lockfile)
        TimeECHO(f"进行同步判定{i}")
        sleeploop = 0
        for sleeploop in np.arange(60*5*(totalnode-1)):
          # 主辅通信循环
          if os.path.exists(filename) and not 主辅通信成功:
            myrandom = self.readfile(filename)[0].strip()
          if len(myrandom) > 0 and not 主辅通信成功:
            lockfile = f".tmp.barrier.{myrandom}.{i}.{name}.in.txt"
            lockfile = os.path.join(Settings.tmpdir, lockfile)
            TimeECHO(f"同步文件更新 lockfile=[{lockfile}]")
            sleep(10)
            主辅通信成功 = self.removefile(lockfile)
            if not 主辅通信成功:
              TimeECHO(f"发现异常,主辅通信成功={主辅通信成功},无法删除[{lockfile}]")
              if os.path.exists(lockfile):
                TimeECHO(f"发现异常, 不存在[{lockfile}], 跳出循环")
                break
          #
          # 本节点通信成功，开始等待其他节点
          if 主辅通信成功:
            hour, minu, sec = self.time_getHMS()
            if sleeploop % 10 == 0:
              TimeECHO(f"本节点通信成功{sleeploop}，正在寻找>{全部通信成功文件}<")
            if os.path.exists(全部通信成功文件):
              TimeECHO(f"检测到全部通信成功文件{全部通信成功文件}")
              同步成功 = True
              break
          if os.path.exists(全部通信失败文件):
            TimeErr(f"监测到全部通信失败文件{全部通信失败文件}")
            同步成功 = False
            break
          sleep(1)
    # 到此处完成
    # 因为是逐一进行同步的,所以全部通信成功文件只能由最后一个node负责删除
    if not 同步成功:
      self.touch同步文件(全部通信失败文件)
    同步成功 = 同步成功 and not os.path.exists(全部通信失败文件)
    file_sleeptime = ".tmp.barrier.sleeptime.txt"
    file_sleeptime = os.path.join(Settings.tmpdir, file_sleeptime)
    if 同步成功:
      TimeECHO("同步等待成功")
      if ionode:
        TimeECHO(f"存储sleeptime到 [{file_sleeptime}]")
        self.touchfile(filename=file_sleeptime, content=str(sleeptime))
        TimeECHO("开始删建文件")
        self.clean文件()
        self.touchfile(全部通信成功文件)
        self.removefile(同步文件)
        self.removefile(全部通信失败文件)
      else:
        TimeECHO("开始读取sleeptime")
        sleeptime_read = self.readfile(file_sleeptime)[0].strip()
        if len(sleeptime_read) > 0:
          sleeptime = int(sleeptime_read)
    else:
      TimeErr("同步等待失败")
      self.touchfile(全部通信失败文件)
      return False
    #
    self.barriernode(mynode, totalnode, "同步等待结束")
    TimeECHO(f"需要sleep{sleeptime}")
    sleep(sleeptime)
    self.removefile(file_sleeptime)
    return not os.path.exists(同步文件)

  @staticmethod
  def time_getHM():
    current_time = datetime.now(Settings.eastern_eight_tz)
    hour = current_time.hour
    minu = current_time.minute
    return hour, minu

  @staticmethod
  def time_getHMS():
    current_time = datetime.now(Settings.eastern_eight_tz)
    hour = current_time.hour
    minu = current_time.minute
    sec = current_time.second
    return hour, minu, sec

  @staticmethod
  def time_getYHMS():
    current_time = datetime.now(Settings.eastern_eight_tz)
    year = current_time.hour
    hour = current_time.hour
    minu = current_time.minute
    sec = current_time.second
    return year, hour, minu, sec

  @staticmethod
  def time_getweek():
    return datetime.now(Settings.eastern_eight_tz).weekday()
  # return 0 - 6

  @staticmethod
  def hour_in_span(startclock=0, endclock=24, hour=None):
    if not hour:
      hour, minu, sec = DQWheel.time_getHMS()
      hour = hour + minu/60.0+sec/60.0/60.0
    startclock = (startclock+24) % 24
    endclock = (endclock+24) % 24
    #
    # 全天
    if startclock == endclock:
      return 0
    # 不跨越午夜的情况[6,23]
    if startclock <= endclock:
      left = 0 if startclock <= hour <= endclock else DQWheel.left_hour(startclock, hour)
    # 跨越午夜的情况[23,6], 即[6,23]不对战
    else:
      left = DQWheel.left_hour(startclock, hour) if endclock < hour < startclock else 0
    return left

  @staticmethod
  def left_hour(endtime=24, hour=None):
    if not hour:
      hour, minu, sec = DQWheel.time_getHMS()
      hour = hour + minu/60.0+sec/60.0/60.0
    left = (endtime+24-hour) % 24
    return left

  def stoptask(self):
    TimeErr(f"停止Airtest控制,停止信息"+self.stopinfo)
    return
    # 该命令无法结束,直接return吧
    # sys.exit()

