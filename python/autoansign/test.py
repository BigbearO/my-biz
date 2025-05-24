import os
import sys
import tempfile

if __name__ == '__main__':
  # prefix = "info"
  # data= tmpdir = os.path.join(tempfile.gettempdir(), prefix)
  # print(data)
  tmpdir = os.path.abspath('.')
  print(tmpdir)
