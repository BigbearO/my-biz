import sys
from airtest_mobileauto import Settings
from autoYuanShen.app_yuan_shen import AppYuanShen


def main():
    config_file = ""
    if len(sys.argv) > 1:
        config_file = str(sys.argv[1])
    Settings.Config(config_file)
    ce = AppYuanShen()
    ce.run()
    exit()


if __name__ == "__main__":
    main()
