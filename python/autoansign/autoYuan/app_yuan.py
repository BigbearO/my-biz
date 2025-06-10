from airtest.core.api import touch, sleep

from framework.infra.common.common_func import Template
from framework.infra.common.property import AutoProperty
from framework.task.BaseTask import BaseTask

logInfo = AutoProperty.logInfo
# 用来测试的

class AppYuan(BaseTask):

    @classmethod
    def support_game(cls):
        # todo 测试之后再更新
        return "com.yuliang.wuziqigame"

    # todo 参数传递优化 怎么传进去
    """
    package_name ,包名，每个app都有唯一包名
    account 账号 用户账号 -用于自动切换
    link 模拟器地址
    taskMode 游戏模式类型
    """
    def __init__(self,app_id ,account,link,task_mode):
        super().__init__(account,link,app_id)
        self.prefix = self.__class__.__name__  # 类的名字
        self.app_id =  app_id
        self.dayFILE = f"{self.prefix}.txt"
        self.task_mode=task_mode
        logInfo.TimeECHO("init finished")

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
            self.run()


    # todo 优化一下逻辑
    def run(self, times=0):
        if not self.device_is_connect():
            self.device_connect()
        if times > 8:
            logInfo.TimeECHO("失败次数太多，停止")
            return

        times = times + 1

        # ------------------------------------------------------------------------------
        # 不存在对应图片则设置为None
        startApp = Template(r"wuziqi_.png", record_pos=(-1, -0.224), resolution=(960, 540))

        elements = []
        startGame = Template(r"wechat_20250525225310.png", record_pos=(-5, -0.224), resolution=(540,960))
        # elements.append(startGame)

        #
        # if startApp:
        # todo 暂时先这样
        touch(startApp)
        sleep(3)
        touch(startGame)

        # self.Tool.touchfile(self.dayFILE, content=str(self.yesterday))

        return



    def stop(self):
        # todo 推出当前游戏程序
        print("stop")
