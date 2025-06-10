# todo 引入当前包下其他文件，方便其他模块调用
# 并且 可以执行config初始化,init文件里面的代码在被其他地方引入时执行
# 就想js 一样 定义个函数，然后隔开一行直接调用就行

#  todo 在此方法中读取配置文件，来配置相关
def init_config():
    print("init finished")


init_config()
