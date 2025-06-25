
from abc import abstractmethod

# todo 逻辑不复杂可以考虑run,逻辑复杂考虑责任链模式，
class IStep:

  @abstractmethod
  def logic(self):
    pass
  # 排序值，越小越前面
  @abstractmethod
  def order(self):
    pass
