# !/usr/bin/env python3
# -*- coding: utf-8 -*-

import logging
from datetime import datetime

from framework.common.auto_constants import TimeZoneInfo





# todo 之后再看看怎么实例化这些配置参数

class AutoLog:
  # 日志参数
  logger_dict = [logging.DEBUG, logging.INFO, logging.WARNING, logging.ERROR,
                 logging.CRITICAL]
  def __init__(self):
    logger_level = 1  # 设置为0输出详细模式
    self.outputnode = -1
    self.logfile = "result.0.txt"
    self.prefix=""
    self.node="1"
    self.logfile_dict = {0: "result.0.txt"}
    logging.basicConfig(level=self.logger_dict[logger_level], format='%(message)s')
    # todo 之后深入学习后再优化
    self.logger = logging.getLogger("airtest_mobile")
    self.logger.setLevel(self.logger_dict[logger_level])
  # todo 之后优化
  def loggerhead(self):
    node=self.node
    eastern_eight_tz= AutoProperty.timeZoneInfo.eastern_eight_tz
    prefix=AutoProperty.logInfo.prefix
    # 获取当前日期和时间
    current_datetime = datetime.now(eastern_eight_tz)
    # 格式化为字符串（月、日、小时、分钟、秒）
    formatted_string = current_datetime.strftime("[%m-%d %H:%M:%S]")
    return formatted_string+f"{prefix}{node}>"

  def TimeECHO(self,info, *args, **kwargs):
    modified_args = self.loggerhead()+info
    logfile= self.logfile
    if len(self.logfile) > 0:
      f = open(logfile, 'a', encoding='utf-8')
      f.write(modified_args+"\n")
      f.close()
    self.logger.info(modified_args)
    return


  def TimeErr(self,info, *args, **kwargs):
    logfile=self.logfile
    modified_args = self.loggerhead()+info
    if len(logfile) > 0:
      f = open(logfile, 'a', encoding='utf-8')
      f.write(modified_args+"\n")
      f.close()

    log = self.logger
    log.warning(modified_args, *args, **kwargs)

  def TimeDebug(self,info, *args, **kwargs):
    self.logger.debug(self.loggerhead()+info, *args, **kwargs)



class SimulatorInfo:
  def __init__(self):
    self.mynode=0
    self.address=''




class AutoProperty:
  # todo 初始化logger后全局使用这个
  logInfo =AutoLog()
  timeZoneInfo=TimeZoneInfo()
  simulatorInfo= SimulatorInfo()

  @classmethod
  def init_property(cls, config_file="config/config.yaml"):
    pass
