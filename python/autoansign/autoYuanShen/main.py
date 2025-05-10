import sys
from airtest_mobileauto import Settings
from autoYuanShen.app_yuan_shen import AppYuanShen
from framework.consumer.abs_consumer import AutoConsumer


def test_main():
    config_file = ""
    if len(sys.argv) > 1:
        config_file = str(sys.argv[1])
    Settings.Config(config_file)
    ce = AppYuanShen()
    ce.run()
    exit()

def handle(data):
    print(data)

if __name__ == "__main__":
    endpoint=""
    # todo 配置化
    consumer=AutoConsumer(handle,endpoint=endpoint,group="auto-games")
    consumer.run("games","0")

