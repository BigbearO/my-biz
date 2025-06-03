from airtest.core.api import touch

from framework.app.app_operation import AppOB
from framework.common.common_func import Template
from framework.common.property import AutoProperty
from framework.entity.baseTask import BaseTask
from framework.phone.device import DeviceOB
from framework.phone.dqWheel import DQWheel

logInfo = AutoProperty.logInfo
# 用来测试的

class AppYuanShen(BaseTask):

    @classmethod
    def support_game(cls):
        return 1

    # todo 参数传递优化 怎么传进去
    """
    package_name ,包名，每个app都有唯一包名
    account 账号 用户账号 -用于自动切换
    link 模拟器地址
    game 游戏类型
    """
    def __init__(self,package_name ,account,link,game,mobile_type='android'):
        super().__init__(account,link,game)
        self.prefix = self.__class__.__name__  # 类的名字

        self.mobile = DeviceOB(device_type=mobile_type, teams=None, link=self.link)
        self.device_type = self.mobile.device_type
        self.APPID =  package_name
        self.APPOB = AppOB(APPID=self.APPID, big=True, device=self.mobile)
        dict_file =f"{self.mobile.device_type}.var_dict_{self.prefix}.yaml"
        self.Tool = DQWheel(var_dict_file=dict_file)
        self.dayFILE = f"{self.prefix}.txt"
        self.today = self.Tool.time_getweek()
        self.yesterday = (self.today - 1) % 7
        self.APPOB.big = False
        #
        self.looptime = 1
        logInfo.TimeECHO("init finished")
        self.success_count = 0

    # 循环执行指定次数
    def loop_run(self, times=1):
        if times<=0:
            return
        count=0
        while True:
          if count >=times:
              break
          count = count + 1
          logInfo.TimeECHO("当前第="+count+"次运行")
          self.success_count = 0
          self.run()

    def stop(self):
        self.APPOB.app_close()


    # todo 优化一下逻辑
    def run(self, times=0):
        if not self.mobile.is_connect():
            self.mobile.device_connect()

            # self.Tool.timelimit(timekey="RUN", limit=self.timelimit, init=True)
        #
        # if self.Tool.timelimit(timekey="RUN", limit=self.timelimit, init=False):
        #     logInfo.TimeECHO(f"{self.prefix}.运行超时")
        #     self.Tool.touchfile(self.dayFILE, content=str(self.yesterday))
        #     return
        #
        if times > 8:
            logInfo.TimeECHO("失败次数太多，停止")
            self.Tool.touchfile(self.dayFILE, content=str(self.yesterday))
            return

        times = times + 1
        # 重新打开程序 todo 是否需要？
        # self.APPOB.app_restart()

        # ------------------------------------------------------------------------------
        # 不存在对应图片则设置为None
        startApp = Template(r"wuziqi_.png", record_pos=(-1, -0.224), resolution=(960, 540))

        elements = []
        startGame = Template(r"wechat_20250525225310.png", record_pos=(-5, -0.224), resolution=(540,960))
        # elements.append(startGame)

        #
        # if startApp:
            # self.Tool.existsTHENtouch(startGame, self.prefix+"startGame", savepos=False)

        # touch(startApp)
        # sleep(1)
        touch(startGame)

        # self.Tool.touchfile(self.dayFILE, content=str(self.yesterday))
        self.APPOB.app_close()
        return



