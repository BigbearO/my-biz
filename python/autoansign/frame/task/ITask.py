# 基本类，包含常规模拟器地址,任务类型等
import os
import platform
import subprocess
from abc import abstractmethod


# interface for task ,  静态类方法才在这儿实现
class ITask:
  @classmethod
  def support_game(cls):
    pass



  @classmethod
  def is_support_game(cls, game_type):
    pass

  @abstractmethod
  def simulator_hide(self):
    pass

  @abstractmethod
  def simulator_show(self):
    pass


  # 启动模拟器
  # 实际上可以启动所有指定路径的程序，兼容 Windows、macOS 和 Linux。
  @classmethod
  def simulator_start(cls, program_path):
    system = platform.system()  # 获取操作系统类型
    try:
      if system == "Windows":
        # Windows 系统
        if os.path.isfile(program_path):
          os.startfile(program_path)  # 使用 os.startfile 启动程序
          print(f"在 Windows 上启动程序: {program_path}")
        else:
          raise FileNotFoundError(f"程序路径无效: {program_path}")

      elif system == "Darwin":  # macOS
        # macOS 系统
        if os.path.isdir(program_path) and program_path.endswith(".app"):
          # 如果是 .app 文件夹，使用 'open -a'
          subprocess.run(["open", "-a", program_path], check=True)
          print(f"在 macOS 上启动应用程序: {program_path}")
        elif os.path.isfile(program_path):
          # 如果是可执行文件，直接运行
          subprocess.run([program_path], check=True)
          print(f"在 macOS 上启动程序: {program_path}")
        else:
          raise FileNotFoundError(f"程序路径无效: {program_path}")
      elif system == "Linux":
        # Linux 系统
        if os.path.isfile(program_path):
          # 直接运行可执行文件
          subprocess.run([program_path], check=True)
          print(f"在 Linux 上启动程序: {program_path}")
        else:
          raise FileNotFoundError(f"程序路径无效: {program_path}")

      else:
        raise NotImplementedError(f"不支持的操作系统: {system}")

    except (FileNotFoundError, subprocess.CalledProcessError) as e:
      print(f"启动程序失败: {e}")

  @abstractmethod
  def device_is_connect(self):
    pass


  @abstractmethod
  def app_start(self):
    pass


  @abstractmethod
  def app_restart(self, sleeptime=0):
    pass


  @abstractmethod
  def app_stop(self):
    pass


  @abstractmethod
  def install_app(self):
    pass




  @abstractmethod
  def run(self):
    pass
