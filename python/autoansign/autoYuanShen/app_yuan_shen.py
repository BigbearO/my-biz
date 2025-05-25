from airtest.core.api import sleep

from framework.app.app_operation import AppOB
from framework.common.common_func import connect_status, Template, exists
from framework.common.property import AutoProperty
from framework.entity.task import BaseTask
from framework.phone.device import deviceOB
from framework.phone.dqWheel import DQWheel

logInfo = AutoProperty.logInfo


# 用来测试的

class AppYuanShen(BaseTask):

    @classmethod
    def support_game(cls):
        return 1

    # todo 参数传递优化 怎么传进去
    def __init__(self,package_name ,account,link,game):
        super().__init__(account,link,game)
        self.prefix = self.__class__.__name__  # 类的名字

        self.mobile = deviceOB(device_type='android', teams=None, link=self.link)
        self.device_type = self.mobile.device_type
        self.APPID =  package_name
        self.APPOB = AppOB(APPID=self.APPID, big=True, device=self.mobile)
        dict_file =f"{self.mobile.device_type}.var_dict_{self.prefix}.yaml"
        self.Tool = DQWheel(var_dict_file=dict_file)
        self.dayFILE = f"{self.prefix}.txt"
        self.timelimit = 60 * 10*2
        self.run_time_range = [0.0, 23.0]
        self.today = self.Tool.time_getweek()
        self.yesterday = (self.today - 1) % 7
        self.APPOB.big = False
        #
        self.looptime = 1
        logInfo.TimeECHO("init finished")
        self.success_count = 0


    def loop_run(self, times=0):
        times = times + 1
        startclock = self.run_time_range[0]
        endclock = self.run_time_range[1]
        while True:
            leftmin = self.Tool.hour_in_span(startclock, endclock) * 60.0
            if leftmin > 0:
                logInfo.TimeECHO("剩余%d分钟进入新的一天" % (leftmin))
                self.APPOB.app_close()
                self.mobile.device_reconnect(leftmin * 60)
                break
            times = times + 1
            logInfo.TimeECHO("=" * 10)
            self.success_count = 0
            self.run()


    def stop(self):
        self.APPOB.app_close()
    # todo 优化一下逻辑
    def run(self, times=0):
        if not connect_status():
            self.mobile.device_connect()
        if times == 0:
            self.today = self.Tool.time_getweek()
            self.yesterday = (self.today - 1) % 7
            try:
                self.yesterday = int(self.Tool.readfile(self.dayFILE)[0].strip())
            except:
                logInfo.TimeECHO(f"未能从{self.dayFILE}中获取到上次运行时间")

            self.Tool.timelimit(timekey="RUN", limit=self.timelimit, init=True)
        #
        if self.Tool.timelimit(timekey="RUN", limit=self.timelimit, init=False):
            logInfo.TimeECHO(f"{self.prefix}.运行超时")
            self.Tool.touchfile(self.dayFILE, content=str(self.yesterday))
            return
        #
        if times > 8:
            logInfo.TimeECHO("失败次数太多，停止")
            self.Tool.touchfile(self.dayFILE, content=str(self.yesterday))
            return

        times = times + 1
        # 重新打开程序
        self.APPOB.app_restart()

        # ------------------------------------------------------------------------------
        # 不存在对应图片则设置为None
        startGame = Template(r"wechat_20250525225310.png", record_pos=(-50, -0.224), resolution=(960, 540))

        elements = []
        elements.append(Template(r"tpl1725544480761.png", record_pos=(0.226, 0.013), resolution=(960, 540)))

        #
        if(startGame):
            self.Tool.existsTHENtouch(startGame, self.prefix+"startGame", savepos=False)

        # self.Tool.touchfile(self.dayFILE, content=str(self.yesterday))
        self.APPOB.app_close()
        return True



