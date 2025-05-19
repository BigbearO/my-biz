import traceback

import yaml
from framework.common.base_func import funs_name
from framework.common.property import AutoProperty

logInfo = AutoProperty.logInfo

class readyaml():
  def __init__(self, yamlfile=""):
    self.yamlfile = yamlfile
    try:
      with open(yamlfile, 'r', encoding='utf-8') as f:
        yaml_dict = yaml.load(f, Loader=yaml.FullLoader)
      logInfo.TimeECHO(f"{funs_name()}.读取{yamlfile}")
      if not isinstance(yaml_dict, dict):
        logInfo.TimeECHO(f"{funs_name()}.读取{yamlfile}. 内容异常, 设置为空数组")
        yaml_dict = {}
      self.yaml_dict = yaml_dict
    except:
      traceback.print_exc()
      logInfo.TimeECHO(f"{funs_name()}.读取{yamlfile}失败")
      self.yaml_dict = {}

  def get(self, tag="", fallback=None):
    if tag in self.yaml_dict.keys():
      fallback = self.yaml_dict[tag]

    return fallback

  def getint(self, tag="", fallback=None):
    try:
      fallback = self.get(tag, fallback)
      fallback = int(fallback)
    except:
      logInfo.TimeECHO(
        f"{funs_name()}.读取{self.yamlfile}失败，参数{tag}设置错误,采用默认值")
    return fallback

  def getboolean(self, tag="", fallback=None):
    try:
      fallback = self.get(tag, fallback)
      fallback = bool(fallback)
    except:
      logInfo.TimeECHO(
        f"{funs_name()}.读取{self.yamlfile}失败，参数{tag}设置错误,采用默认值")
    #
    return fallback

  #

  def getstr(self, tag="", fallback=None):
    try:
      fallback = self.get(tag, fallback)
      fallback = str(fallback)
    except:
      logInfo.TimeECHO(
        f"{funs_name()}.读取{self.yamlfile}失败，参数{tag}设置错误,采用默认值")
    #
    return fallback
