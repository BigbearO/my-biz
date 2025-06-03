import os
import sys
import tempfile

import keyboard

from frame.infra.common.auto_constants import AutoConstants

if __name__ == '__main__':
  # prefix = "info"
  # data= tmpdir = os.path.join(tempfile.gettempdir(), prefix)
  # print(data)
  # tmpdir = os.path.abspath('.')
  # print(tmpdir)
  keyboard.press_and_release(AutoConstants.BossKey)
