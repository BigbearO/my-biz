package org.dromara.goodfish.meta.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.goodfish.meta.constants.GoodfishMetaConstants;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 监听租约原子操作层（manager）.
 *
 * <p>账号级 Redis 租约：同一闲鱼账号同一时刻只允许一台设备监听（否则重复回复）。
 * acquire / renew / release 均以 Lua 脚本保证原子性（AGENTS.md 分层规范）.</p>
 *
 * @author goodfish
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListenLeaseManager {

    /**
     * 申请结果：ACQUIRED 新占用 / RENEWED 自己重入续期 / CONFLICT 他人占用 / DEGRADED Redis 不可用降级.
     */
    public enum AcquireResult {
        ACQUIRED, RENEWED, CONFLICT, DEGRADED
    }

    /**
     * 续期结果：RENEWED 成功 / NOT_HOLDER 非持有者 / DEGRADED Redis 不可用降级.
     */
    public enum RenewResult {
        RENEWED, NOT_HOLDER, DEGRADED
    }

    /**
     * 释放结果：RELEASED 成功 / NOT_HOLDER 非持有者 / DEGRADED Redis 不可用降级.
     */
    public enum ReleaseResult {
        RELEASED, NOT_HOLDER, DEGRADED
    }

    /**
     * 申请/重入：key 不存在 → 占用；value 为自己 → 续期；他人占用 → 冲突.
     */
    private static final String LUA_ACQUIRE = """
        if redis.call('exists', KEYS[1]) == 0 then
          redis.call('set', KEYS[1], ARGV[1])
          redis.call('expire', KEYS[1], ARGV[2])
          return 1
        end
        if redis.call('get', KEYS[1]) == ARGV[1] then
          redis.call('expire', KEYS[1], ARGV[2])
          return 2
        end
        return 0
        """;

    /**
     * 续期：仅持有者（value 相等）可重置 TTL.
     */
    private static final String LUA_RENEW = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
          redis.call('expire', KEYS[1], ARGV[2])
          return 1
        end
        return 0
        """;

    /**
     * 释放：仅持有者可删除（Lua 内比较即删，避免误删他人租约）.
     */
    private static final String LUA_RELEASE = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
          return redis.call('del', KEYS[1])
        end
        return 0
        """;

    /**
     * 申请监听占用.
     *
     * @param goodfishId      闲鱼用户 ID
     * @param clientInstanceId 客户端实例标识（机器码 + 本地服务启动 ID）
     * @return 申请结果；Redis 不可用时返回 DEGRADED（调用方降级放行）
     */
    public AcquireResult acquire(String goodfishId, String clientInstanceId) {
        String key = buildLeaseKey(goodfishId);
        try {
            int result = eval(LUA_ACQUIRE, key, clientInstanceId);
            if (result < 0) {
                return AcquireResult.DEGRADED;
            }
            return switch (result) {
                case 1 -> AcquireResult.ACQUIRED;
                case 2 -> AcquireResult.RENEWED;
                default -> AcquireResult.CONFLICT;
            };
        } catch (Exception e) {
            log.warn("申请监听租约失败，降级放行 goodfishId={}", goodfishId, e);
            return AcquireResult.DEGRADED;
        }
    }

    /**
     * 续期监听租约（仅持有者可续）.
     */
    public RenewResult renew(String goodfishId, String clientInstanceId) {
        String key = buildLeaseKey(goodfishId);
        try {
            return eval(LUA_RENEW, key, clientInstanceId) == 1 ? RenewResult.RENEWED : RenewResult.NOT_HOLDER;
        } catch (Exception e) {
            log.warn("续期监听租约失败，降级放行 goodfishId={}", goodfishId, e);
            return RenewResult.DEGRADED;
        }
    }

    /**
     * 释放监听租约（仅持有者可释放）.
     */
    public ReleaseResult release(String goodfishId, String clientInstanceId) {
        String key = buildLeaseKey(goodfishId);
        try {
            return eval(LUA_RELEASE, key, clientInstanceId) == 1 ? ReleaseResult.RELEASED : ReleaseResult.NOT_HOLDER;
        } catch (Exception e) {
            log.warn("释放监听租约失败，降级放行 goodfishId={}", goodfishId, e);
            return ReleaseResult.DEGRADED;
        }
    }

    /**
     * 组装租约 Redis key：goodfish:listen:{goodfish_id}.
     */
    private String buildLeaseKey(String goodfishId) {
        return GoodfishMetaConstants.LISTEN_LEASE_KEY_PREFIX + goodfishId;
    }

    /**
     * 执行 Lua 脚本（keys = [租约key]，argv = [clientInstanceId, ttlSeconds]）.
     *
     * @return Lua 返回的整数；无有效返回值时返回 -1
     */
    private int eval(String lua, String key, String clientInstanceId) {
        RedissonClient client = RedisUtils.getClient();
        RScript script = client.getScript();
        List<Object> keys = Collections.singletonList(key);
        Object result = script.eval(RScript.Mode.READ_WRITE, lua, RScript.ReturnType.VALUE, keys,
            clientInstanceId, String.valueOf(GoodfishMetaConstants.LISTEN_LEASE_TTL_SECONDS));
        return result instanceof Number number ? number.intValue() : -1;
    }

}
