from framework.steps.IStep import IStep


class MyStep(IStep):
  def logic(self):
    print(self.order())

  def order(self):
    return 1
