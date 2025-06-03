# 基本类，包含常规模拟器地址,任务类型等
from abc import abstractmethod


class BaseTask:
  def __init__(self, account, link, game):
    self.account = account
    self.link = link  # 连接的模拟器地址
    self.teams = []
    self.isMain = True  # 房主为true，否则为false
    self.game = game  # 游戏类型
    self.task_mode = 1  # 任务类型，单人，组队
    self.device = None

  @classmethod
  def support_game(cls):
    pass

  @classmethod
  def is_support_game(cls, game_type):
    game = cls.support_game()
    return game == game_type

  # @abstractmethod
  # def start_app(self):
  #   pass
  #
  # @abstractmethod
  # def stop_app(self):
  #   pass

  @abstractmethod
  def run(self):
    pass
