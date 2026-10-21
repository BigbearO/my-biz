package org.dromara.goodfish.meta.service;

/**
 * 监听租约Service接口（跨设备去重：同一闲鱼账号同一时刻只允许一台设备监听）.
 *
 * @author goodfish
 */
public interface IListenLeaseService {

    /**
     * 申请监听占用.
     *
     * <p>已被其他设备占用 → 40901；Redis 不可用 → 降级放行（记 WARN）.</p>
     *
     * @param goodfishId       闲鱼用户 ID
     * @param clientInstanceId 客户端实例标识
     * @return 租约有效期（秒）
     */
    int acquire(String goodfishId, String clientInstanceId);

    /**
     * 续期监听租约（仅持有者可续；Redis 不可用 → 降级放行）.
     */
    void renew(String goodfishId, String clientInstanceId);

    /**
     * 释放监听占用（幂等：非持有者/已过期均按成功处理）.
     */
    void release(String goodfishId, String clientInstanceId);

}
