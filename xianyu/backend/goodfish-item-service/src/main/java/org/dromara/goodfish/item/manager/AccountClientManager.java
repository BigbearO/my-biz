package org.dromara.goodfish.item.manager;

import account.v1.Account;
import account.v1.AccountServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;
import org.springframework.stereotype.Component;

/**
 * 账号服务 gRPC 客户端原子操作层（manager）.
 *
 * <p>商品同步时将闲鱼账号 ID 解析为云端 account_id（同步的前置步骤）.</p>
 *
 * @author goodfish
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClientManager {

    private final AccountServiceGrpc.AccountServiceBlockingStub accountServiceBlockingStub;

    /**
     * 按闲鱼用户 ID 查询账号.
     *
     * @return 账号信息；账号不存在（NOT_FOUND）返回 null
     * @throws ServiceException 账号服务不可用（区别于账号不存在的基础设施故障）
     */
    public Account.AccountInfo getByGoodfishId(String goodfishId) {
        try {
            return accountServiceBlockingStub.getAccountByGoodfishId(
                Account.GetAccountByGoodfishIdRequest.newBuilder()
                    .setGoodfishId(goodfishId == null ? "" : goodfishId)
                    .build());
        } catch (StatusRuntimeException e) {
            if (Status.Code.NOT_FOUND == e.getStatus().getCode()) {
                return null;
            }
            log.warn("[{}] 调用账号服务 GetAccountByGoodfishId 失败 goodfishId={}, status={}",
                GoodfishItemConstants.LOGGER_ITEM_SERVICE, goodfishId, e.getStatus(), e);
            throw new ServiceException(GoodfishItemConstants.MSG_ACCOUNT_SERVICE_UNAVAILABLE);
        }
    }

}
