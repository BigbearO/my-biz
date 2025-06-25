from framework.steps.MyStep import MyStep

data=[MyStep(),MyStep()]
sortedData = sorted(data, key=lambda obj: obj.order())
