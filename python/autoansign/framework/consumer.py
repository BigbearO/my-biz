import functools
import time
from concurrent.futures.thread import ThreadPoolExecutor
import multiprocessing
from rocketmq import ClientConfiguration, Credentials, SimpleConsumer, \
  FilterExpression



core_count = multiprocessing.cpu_count()
#   todo 暂定当前cpu线程数
thread_count = core_count if core_count <= 1 else (core_count)
consume_executor = ThreadPoolExecutor(max_workers=thread_count,
                                      thread_name_prefix="consume-message")


def consume_message(consumer, message):
  try:
    consumer.ack(message)
    print(f"ack message:{message.message_id}.")
  except Exception as exception:
    print(f"consume message raise exception: {exception}")


def receive_callback(receive_result_future, consumer):
  messages = receive_result_future.result()
  print(f"{consumer.__str__()} receive {len(messages)} messages.")
  for msg in messages:
    try:
      # consume message in other thread, don't block the async receive thread
      print(123)
      body_data = str(msg.body, encoding = "utf-8")
      print(body_data)
      consume_executor.submit(consume_message, consumer=consumer, message=msg)
    except Exception as exception:
      print(f"receive message raise exception: {exception}")


def shutdown_callback(is_exception, consumer):
  consumer.shutdown()
  if is_exception:
    # 异常退出可以发警报
    print("111")


if __name__ == '__main__':
  endpoints = "192.168.50.77:8081"
  credentials = Credentials()
  # if auth enable
  # credentials = Credentials("ak", "sk")
  config = ClientConfiguration(endpoints, credentials)
  # with namespace
  # config = ClientConfiguration(endpoints, credentials, "namespace")
  topic = "games"
  # topic = "auto-games"
  # in most case, you don't need to create too many consumers, singleton pattern is recommended
  # close the simple consumer when you don't need it anymore
  simple_consumer = SimpleConsumer(config, "auto-games")
  try:
    simple_consumer.startup()
    try:
      # todo 常量定义
      simple_consumer.subscribe(topic, FilterExpression("0"))
      while True:
        try:
          time.sleep(6)
          # max message num for each long polling and message invisible duration after it is received
          future = simple_consumer.receive_async(32, 15)
          future.add_done_callback(
              functools.partial(receive_callback, consumer=simple_consumer))
        except Exception as e:
          print(
              f"{simple_consumer.__str__()} receive topic:{topic} raise exception: {e}")
    except Exception as e:
      print(
          f"{simple_consumer.__str__()} subscribe topic:{topic} raise exception: {e}")
      simple_consumer.shutdown()
  except Exception as e:
    print(f"{simple_consumer.__str__()} startup raise exception: {e}")
    shutdown_callback(True, simple_consumer)
