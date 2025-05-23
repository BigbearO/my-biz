
# 基本类，包含常规模拟器地址,任务类型等
class BaseTask:
    def __init__(self,account,link):
      self.account=account
      self.credential=""
      self.link=link # 连接的模拟器地址
      self.team=[]
      self.isMain=True # 房主为true，否则为false
      self.game=-1 #游戏类型
      self.task_mode="" #任务类型，单人，组队


