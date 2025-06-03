# # .......
# import multiprocessing
# import traceback
#
# from frame.common.property import AutoProperty
# from frame.common.settins import Settings
#
#
# logInfo = AutoProperty.logInfo
#
# # todo 这个manager  为组队做准备
# class TaskManager:
#   def __init__(self, config_file, task_class, method_name, *args, **kwargs):
#     self.task_class = task_class
#     self.method_name = method_name
#     self.args = args
#     self.kwargs = kwargs
#     self.config_file = config_file
#     # 初次读入参数，用于确定单/多进程执行
#     Settings.Config(self.config_file)
#     Settings.info("Task_init")
#
#   def execute(self):
#     logInfo.TimeDebug("task execute"+str(Settings.multiprocessing))
#     if Settings.multiprocessing:
#       self.multi_execute()
#     else:
#       self.single_execute()
#
#   def single_execute(self, mynode=None):
#     # 子进程重新配置参数
#     Settings.Config(self.config_file)
#     #
#     if mynode != None:
#       logInfo.TimeDebug("input mynode=%s", mynode)
#       # 多进程时, mynode无法读入, 这里进行设置
#       Settings.Config_mynode(mynode)
#     else:
#       mynode = Settings.mynode
#       logInfo.TimeDebug("reD mynode=%s", mynode)
#     Settings.info("single_exe")
#     #
#     logInfo.TimeDebug("single_execute starting with mynode=%s", mynode)
#     try:
#       task_instance = self.task_class(*self.args, **self.kwargs)
#       method = getattr(task_instance, self.method_name)
#       method()
#     except:
#       traceback.print_exc()
#       logInfo.TimeErr("Error in single_execute with mynode=%s", mynode)
#     #
#     logInfo.TimeDebug("single_execute finished with mynode=%s", mynode)
#
#   def multi_execute(self):
#     logInfo.TimeDebug("Multiprocessing with %s total nodes.", Settings.totalnode)
#     m_process = Settings.totalnode
#     with multiprocessing.Pool(processes=m_process) as pool:
#       results = pool.map(self.single_execute, range(m_process))
#       logInfo.TimeDebug("Mapping started, waiting for results...")
