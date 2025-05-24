from airtest.core.api import sleep

from framework.app.app_operation import appOB
from framework.common.common_func import connect_status, Template, exists
from framework.common.property import AutoProperty
from framework.entity.task import BaseTask
from framework.phone.device import deviceOB
from framework.phone.dqWheel import DQWheel

logInfo = AutoProperty.logInfo

# package_name ="com.miHoYo.GenshinImpact"
package_name ="com.carrot.carrotfantasy"
# 用来测试的

class AppYuanShen(BaseTask):
    # todo 参数传递优化 怎么传进去
    def __init__(self, account,link):
        super().__init__(account,link)
        self.prefix = self.__class__.__name__  # 类的名字

        self.mobile = deviceOB(device_type=None, teams=None, link=self.link)
        self.device_type = self.mobile.device_type
        self.APPID =  package_name
        self.APPOB = appOB(APPID=self.APPID, big=True, device=self.mobile)
        self.Tool = DQWheel(var_dict_file=f"{self.mobile.device_type}.var_dict_{self.prefix}.yaml")
        self.dayFILE = f"{self.prefix}.txt"
        self.timelimit = 60 * 10
        self.run_time_range = [3.0, 4.0]
        self.today = self.Tool.time_getweek()
        self.yesterday = (self.today - 1) % 7
        self.APPOB.big = False
        #
        self.looptime = 1
        self.success_count = 0

    def stop(self):
        self.APPOB.app_close()
    #
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
        #
        times = times + 1
        # 重新打开程序
        self.APPOB.app_restart()
        #
        # ------------------------------------------------------------------------------
        # 不存在对应图片则设置为None
        开屏广告 = Template(r"tpl1730443076289.png", record_pos=(0.167, -0.224), resolution=(960, 540))
        二级入口 = None
        主页元素 = []
        主页元素.append(Template(r"tpl1725544480761.png", record_pos=(0.226, 0.013), resolution=(960, 540)))
        if 二级入口:
            主页元素.append(二级入口)
        #
        三级入口 = None
        #
        领取按钮 = Template(r"tpl1725544488815.png", record_pos=(-0.214, -0.087), resolution=(960, 540))
        领取成功 = Template(r"tpl1725544509798.png", record_pos=(-0.217, -0.085), resolution=(960, 540))
        领取界面 = []
        领取界面.append(领取按钮)
        领取界面.append(领取成功)
        # ------------------------------------------------------------------------------
        if times % 3 == 2:
            # 强制点击开屏广告
            sleep(2)
            self.Tool.touch_record_pos(record_pos=开屏广告.record_pos, resolution=self.mobile.resolution,
                                       keystr=self.prefix + "开屏广告")
            sleep(2)
        # 打开程序
        for i in range(10):
            存在, 主页元素 = self.Tool.存在任一张图(主页元素, self.prefix + "主页元素")
            if 存在:
                break
            sleep(5)
            self.APPOB.打开APP()
            # 开屏广告
            if 开屏广告:
                self.Tool.existsTHENtouch(开屏广告, self.prefix + "开屏广告", savepos=False)
            #
            存在, 主页元素 = self.Tool.存在任一张图(主页元素, self.prefix + "主页元素")
        # 寻找入口
        if 二级入口:
            self.Tool.存在任一张图([二级入口], self.prefix + "二级入口", savepos=True)
            self.Tool.existsTHENtouch(二级入口, self.prefix + "二级入口", savepos=True)
        #
        if 三级入口:
            self.Tool.存在任一张图([三级入口], self.prefix + "三级入口", savepos=True)
            self.Tool.existsTHENtouch(三级入口, self.prefix + "三级入口", savepos=True)
        #
        存在, 领取界面 = self.Tool.存在任一张图(领取界面, self.prefix + "领取界面")
        if not 存在:
            logInfo.TimeECHO(f"{self.prefix}检测不到领取界面")
            return self.run(times)
        #
        self.Tool.存在任一张图([领取按钮], self.prefix + "领取按钮", savepos=True)
        # ------------------------------------------------------------------------------
        # 下面是特色部分
        # 检测是否有领取按钮，有的话，领取之后再循环一次检测是否有领取成功按钮
        if self.Tool.existsTHENtouch(领取按钮, self.prefix + "领取按钮", savepos=True):
            self.success_count = self.success_count + 1
            logInfo.TimeECHO(f"{self.prefix}.....成功次数{self.success_count}....")
            self.yesterday = self.today
            if self.success_count < self.looptime:
                return self.run(times=0)
        else:
            logInfo.TimeECHO(f"{self.prefix}检测不到领取按钮")
        if exists(领取成功):
            self.yesterday = self.today
            logInfo.TimeECHO(f"{self.prefix}领取成功")
        else:
            if self.yesterday == self.today:
                logInfo.TimeECHO("找不到[领取成功]，应该签到过了")
            else:
                logInfo.TimeECHO("找不到[领取成功]，再次尝试签到")
                return self.run(times)
        #
        self.Tool.touchfile(self.dayFILE, content=str(self.yesterday))
        self.APPOB.app_close()
        return True

    def looprun(self, times=0):
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

