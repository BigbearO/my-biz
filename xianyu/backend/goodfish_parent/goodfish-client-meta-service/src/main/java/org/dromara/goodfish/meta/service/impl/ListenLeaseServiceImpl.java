package org.dromara.goodfish.meta.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.goodfish.meta.constants.GoodfishMetaConstants;
import org.dromara.goodfish.meta.manager.ListenLeaseManager;
import org.dromara.goodfish.meta.manager.ListenLeaseManager.AcquireResult;
import org.dromara.goodfish.meta.manager.ListenLeaseManager.RenewResult;
import org.dromara.goodfish.meta.manager.ListenLeaseManager.ReleaseResult;
import org.dromara.goodfish.meta.service.IListenLeaseService;
import org.springframework.stereotype.Service;

/**
 * 监听租约Service业务层实现.
 *
 * <p>Redis 不可用时降级放行（记 WARN）：可用性优先，重复监听属低频问题.</p>
 *
 * @author goodfish
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ListenLeaseServiceImpl implements IListenLeaseService {

    private final ListenLeaseManager leaseManager;

    /**
     * 申请监听占用.
     */
    @Override
    public int acquire(String goodfishId, String clientInstanceId) {
        validateLeaseParams(goodfishId, clientInstanceId);
        AcquireResult result = leaseManager.acquire(goodfishId, clientInstanceId);
        if (AcquireResult.CONFLICT.equals(result)) {
            log.info("[{}] 监听租约冲突 goodfishId={}",
                GoodfishMetaConstants.LOGGER_LISTEN_LEASE_SERVICE, goodfishId);
            throw new ServiceException(GoodfishMetaConstants.MSG_LEASE_CONFLICT,
                GoodfishMetaConstants.CODE_LEASE_CONFLICT);
        }
        if (AcquireResult.DEGRADED.equals(result)) {
            log.warn("[{}] Redis 不可用，监听租约降级放行 goodfishId={}",
                GoodfishMetaConstants.LOGGER_LISTEN_LEASE_SERVICE, goodfishId);
        }
        return GoodfishMetaConstants.LISTEN_LEASE_TTL_SECONDS;
    }

    /**
     * 续期监听租约.
     */
    @Override
    public void renew(String goodfishId, String clientInstanceId) {
        validateLeaseParams(goodfishId, clientInstanceId);
        RenewResult result = leaseManager.renew(goodfishId, clientInstanceId);
        if (RenewResult.NOT_HOLDER.equals(result)) {
            throw new ServiceException(GoodfishMetaConstants.MSG_LEASE_NOT_HOLDER,
                GoodfishMetaConstants.CODE_LEASE_CONFLICT);
        }
        if (RenewResult.DEGRADED.equals(result)) {
            log.warn("[{}] Redis 不可用，监听租约续期降级放行 goodfishId={}",
                GoodfishMetaConstants.LOGGER_LISTEN_LEASE_SERVICE, goodfishId);
        }
    }

    /**
     * 释放监听占用（幂等）.
     */
    @Override
    public void release(String goodfishId, String clientInstanceId) {
        validateLeaseParams(goodfishId, clientInstanceId);
        ReleaseResult result = leaseManager.release(goodfishId, clientInstanceId);
        if (ReleaseResult.NOT_HOLDER.equals(result)) {
            // 租约已过期/已被他人接管：调用方的占用目标态已达成，按成功处理
            log.info("[{}] 释放时租约已不存在或非本人持有，按成功处理 goodfishId={}",
                GoodfishMetaConstants.LOGGER_LISTEN_LEASE_SERVICE, goodfishId);
        }
    }

    /**
     * 校验租约参数非空.
     */
    private void validateLeaseParams(String goodfishId, String clientInstanceId) {
        if (StringUtils.isBlank(goodfishId)) {
            throw new ServiceException("闲鱼用户ID不能为空");
        }
        if (StringUtils.isBlank(clientInstanceId)) {
            throw new ServiceException("客户端实例标识不能为空");
        }
    }

}
