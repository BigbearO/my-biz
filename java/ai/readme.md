https://cloud.tencent.com/developer/article/2512470
https://blog.csdn.net/qq_35766758/article/details/148388098
https://javaai.pig4cloud.com/spring-ai/api/multimodality

spring ai alibaba graph
https://mp.weixin.qq.com/s/C2AGIJocODXw8nuPMZ_YMg
https://github.com/zhangshenghang/spring-ai-example
https://developer.aliyun.com/article/1671608


java chat
https://gitee.com/taisan/MaxKB4j
https://github.com/alvinlee518/llmchat
https://gitee.com/shop-sparker/spark-x/ 文档：https://x.sparkshop.cn/

智能客服
java
https://gitee.com/easycallcenter365/easycallcenter365
https://github.com/alvinlee518/llmchat


python的
https://github.com/AstrBotDevs/AstrBot
https://github.com/NanGePlus/LangGraphChatBot
https://github.com/RainDayss/AssistGen
https://github.com/xerrors/Yuxi-Know
https://github.com/zhayujie/chatgpt-on-wechat



ChatMemoryEntity 是单个的实体，也是langchain4j对话的上下文存储，MessageHistoryEntity是历史记录，
要求：
1 涉及insert 这些更改添加事务注解
2  修改DbChatMemoryStore ，get 和update ，从单个的ChatMemoryEntity 进行获取消息和更新，update时添加到MessageHistoryEntity，记录消息类型


// todo  

1 构建tokenlistener 参考https://langchain4j.cn/tutorials/observability.html#%E8%81%8A%E5%A4%A9%E6%A8%A1%E5%9E%8B%E5%8F%AF%E8%A7%82%E6%B5%8B%E6%80%A7-chat-model-observability
然后调用 .registerListeners() 进行注册

memId 商品id,+闲鱼买家id， 这样进行分隔 ，token计算时计算itemId,获取租户id,计算token 


1 ws监听的是token 用户级别
 单独的商品id,从消息里面获取参数做隔离  ，并且也是itemId
 memporyId 策略就是  senduserId

2 
 accController 管理账号 cookie这些也是 这个级别的
 建立商品，选择归属账号
 
 每个账号有个ws--账号里面获取商品信息，进行分发处理


todo  
1 能根据cookie str 创建账号
2 根据账户创建 商品，
3 被简化的看看原项目处理，

擦，现在就是不开始就走刷新了，直接拿token进行使用， 和存储，给数据库增加一个字段，
 

 加一个兼容
