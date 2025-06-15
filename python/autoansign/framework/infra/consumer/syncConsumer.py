import json

from rocketmq import ClientConfiguration, Credentials, SimpleConsumer, \
  FilterExpression

# my_ip="http://192.168.50.77:9876"
my_ip="192.168.50.77:8081"
my_group="auto-games"
my_topic = "games"
my_tag="0"

if __name__ == '__main__':
  endpoints = my_ip
  credentials = Credentials()
  # if auth enable
  # credentials = Credentials("ak", "sk")
  config = ClientConfiguration(endpoints, credentials)
  # with namespace
  # config = ClientConfiguration(endpoints, credentials, "namespace")
  topic = my_topic
  # in most case, you don't need to create too many consumers, singleton pattern is recommended
  # close the simple consumer when you don't need it anymore
  simple_consumer = SimpleConsumer(config, my_group)
  try:
    simple_consumer.startup()
    try:
      # simple_consumer.subscribe(topic)
      # use tag filter
      simple_consumer.subscribe(topic, FilterExpression(my_tag))
      while True:
        try:
          # max message num for each long polling and message invisible duration after it is received
          # 同步消费
          messages = simple_consumer.receive(32, 15)
          if messages is not None:
            print(f"{simple_consumer.__str__()} receive {len(messages)} messages.")
            for msg in messages:
              simple_consumer.ack(msg)
              print("123")
              body_data = str(msg.body, encoding = "utf-8")
              print(body_data)
              if body_data.startswith("{"):
                 data=json.loads(body_data)

              simple_consumer.ack(msg)



              print(f"{simple_consumer.__str__()} ack message:[{msg.message_id}].")
        except Exception as e:
          print(f"receive or ack message raise exception: {e}")
    except Exception as e:
      print(f"{simple_consumer.__str__()} subscribe topic:{topic} raise exception: {e}")
      simple_consumer.shutdown()
  except Exception as e:
    print(f"{simple_consumer.__str__()} startup raise exception: {e}")
    simple_consumer.shutdown()
