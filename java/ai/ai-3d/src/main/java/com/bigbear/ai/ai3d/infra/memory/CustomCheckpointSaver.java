package com.bigbear.ai.ai3d.infra.memory;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver.THREAD_ID_DEFAULT;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomCheckpointSaver implements BaseCheckpointSaver {

    private final RedisCheckpointStore redisStore;
    private final PgCheckpointStore pgStore;

    @Value("${spring.ai.agent.checkpoint.max-size:20}")
    private int maxSize;

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        String threadId = resolveThreadId(config);
        List<Checkpoint> checkpoints = redisStore.getCheckpoints(threadId);
        if (checkpoints.isEmpty()) {
            log.debug("Redis 空, fallback 到 PG, threadId={}", threadId);
            checkpoints = pgStore.findRecentCheckpoints(threadId, maxSize);
            if (!checkpoints.isEmpty()) {
                log.debug("PG 查询到 {} 条 checkpoint, 回填 Redis, threadId={}", checkpoints.size(), threadId);
                redisStore.saveCheckpoints(threadId, checkpoints);
            }
        }
        return checkpoints;
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        LinkedList<Checkpoint> checkpoints = new LinkedList<>(list(config));
        return getLast(checkpoints, config);
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        String threadId = resolveThreadId(config);

        pgStore.saveCheckpoint(threadId, checkpoint);

        redisStore.addCheckpoint(threadId, checkpoint);

        return config.withCheckPointId(checkpoint.getId());
    }

    @Override
    public Tag release(RunnableConfig config) throws Exception {
        String threadId = resolveThreadId(config);
        Collection<Checkpoint> checkpoints = list(config);

        redisStore.deleteCheckpoints(threadId);

        return new Tag(threadId, checkpoints);
    }

    private String resolveThreadId(RunnableConfig config) {
        return config.threadId().orElse(THREAD_ID_DEFAULT);
    }
}
