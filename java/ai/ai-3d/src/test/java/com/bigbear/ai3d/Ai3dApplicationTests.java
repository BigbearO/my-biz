package com.bigbear.ai3d;

import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("ai")
class Ai3dApplicationTests {

    RedisSaver redisSaver;
    MysqlSaver mysqlSaver;
    PostgresSaver postgresSaver;
    MessagesModelHook messagesModelHook;
    MemorySaver  memorySaver;


}
