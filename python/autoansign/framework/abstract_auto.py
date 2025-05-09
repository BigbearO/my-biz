from abc import abstractmethod,ABC


# 括号里面的是父类
class AutoGameTemplate(ABC):
    # you can replace with __init__
    @abstractmethod
    def init(self):
        pass

    @abstractmethod
    def run(self):
        pass
    @abstractmethod
    def loop_run(self):
        pass

    @abstractmethod
    def stop(self):
        pass
