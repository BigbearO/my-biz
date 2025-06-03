import functools
import time

from rocketmq import ClientConfiguration, Credentials, SimpleConsumer, \
  FilterExpression

start_sleep = 1
end_sleep = 9


class AutoConsumer:
  def __init__(self,handler, endpoint, group="auto-games", ak="",
      sk=""):
    self.endpoint = endpoint
    self.handler = handler
    # when ak and sk not empty
    if ak is not None and len(ak) > 0 and sk is not None and len(sk) > 0:
      self.credentials = Credentials(ak, sk)
    else:
      self.credentials = Credentials()
    config_data = ClientConfiguration(self.endpoint, self.credentials)
    self.simple_consumer = SimpleConsumer(config_data, group)

  def start(self, listen_topic, filter_tag):
    mq_tag = None
    if filter_tag is not None and len(filter_tag) > 0:
      mq_tag = FilterExpression(filter_tag)
    self.simple_consumer.subscribe(listen_topic, mq_tag)
    while True:
      try:
        time.sleep(start_sleep)
        # max message num for each long polling and message invisible duration after it is received
        future = self.simple_consumer.receive_async(32, 15)
        future.add_done_callback(
            functools.partial(self.receive_callback,
                              consumer=self.simple_consumer))
        time.sleep(end_sleep)
      except Exception as e:
        print(
            f"{self.simple_consumer.__str__()} receive topic:{listen_topic} raise exception: {e}")

  def receive_callback(self,receive_result_future, consumer):
    messages = receive_result_future.result()
    print(f"{consumer.__str__()} receive {len(messages)} messages.")
    for msg in messages:
      try:
        body_data = str(msg.body, encoding="utf-8")
        print(body_data)
        self.handler(body_data)

      except Exception as exception:
        print(f"receive message raise exception: {exception}")
