import os
import traceback

import yaml
from frames.common.base_func import funs_name
from frames.common.property import AutoProperty

logInfo = AutoProperty.logInfo
# todo 直接赋值实例化

def _to_class(cls, _obj):
  _obj_ = type('new', (object,), _obj)
  [setattr(_obj_, key, cls._to_class(value)) if isinstance(value, dict) else setattr(_obj_, key, value) for
   key, value in _obj.items()]
  return _obj_



class ReadConfigFiles(object):
  def __init__(self):
    '''
    ;;获取当前工作路径
    '''
    self.works_path = os.path.dirname(os.path.realpath(__file__))

  @classmethod
  def open_file(cls):
    '''
    ;;读取当前工作目录下cfg.yaml文件内容，并返回字典类型
    :return:
    '''
    return yaml.load(
        open(os.path.join(cls().works_path, r'cfg.yaml'), 'r', encoding='utf-8').read(), Loader=yaml.FullLoader
    )

  @classmethod
  def cfg(cls, item=None):
    '''
    ;;调用该方法获取需要的配置，item如果为None，返回则是全部配置
    :param item:
    :return:
    '''
    return _to_class(cls.open_file().get(item) if item else cls.open_file())

# todo 可以用上面的优化一下，现在的都是简单的值

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
