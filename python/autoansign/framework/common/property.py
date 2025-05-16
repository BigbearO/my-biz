# !/usr/bin/env python3
# -*- coding: utf-8 -*-

import logging





class AutoLog:
  # //todo 在init方法中读取配置文件，重新初始化
  # 日志参数
  logger_dict = [logging.DEBUG, logging.INFO, logging.WARNING, logging.ERROR,
                 logging.CRITICAL]

  @classmethod
  def init_config(cls, config_file="config.yaml"):
    # todo 从配置文件读取
    logger_level = 1  # 设置为0输出详细模式
    cls.outputnode = -1
    cls.logfile = "result.0.txt"
    cls.logfile_dict = {0: "result.0.txt"}
    logging.basicConfig(level=cls.logger_dict[logger_level], format='%(message)s')
    # todo 这个和有package有关系吗，随便取的吗
    cls.logger = logging.getLogger("airtest_mobile")
    cls.logger.setLevel(cls.logger_dict[logger_level])
