
# 基本类，包含常规模拟器地址,任务类型等
class BaseTask:
    def __init__(self):
      self.name=""
      self.address="" # 连接的哪个模拟器
      self.team=[]
      self.isMain=True
      self.game=-1 #游戏类型
      self.task_type=""


