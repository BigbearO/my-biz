package org.dromara.goodfish.admin.manager;

import account.v1.Account;
import account.v1.AccountServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.goodfish.admin.constants.GoodfishAdminConstants;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账号服务 gRPC 客户端原子操作层（manager）.
 *
 * @author goodfish
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClientManager {

    private final AccountServiceGrpc.AccountServiceBlockingStub accountStub;

    /**
     * 循环分页拉取全量账号，按闲鱼账号 ID 建索引（商品列表聚合账号名用）.
     *
     * @return Map&lt;goodfishId, AccountInfo&gt;
     */
    public Map<String, Account.AccountInfo> listAllAccounts() {
        Map<String, Account.AccountInfo> accountMap = new LinkedHashMap<>();
        int page = 1;
        for (int i = 0; i < GoodfishAdminConstants.ACCOUNT_FETCH_MAX_PAGES; i++) {
            Account.ListAccountsResponse response;
            try {
                response = accountStub.listAccounts(Account.ListAccountsRequest.newBuilder()
                    .setPage(page)
                    .setPageSize(GoodfishAdminConstants.ACCOUNT_FETCH_PAGE_SIZE)
                    .build());
            } catch (StatusRuntimeException e) {
                throw GrpcClientErrors.map(e, GoodfishAdminConstants.DESC_ACCOUNT_SERVICE,
                    GoodfishAdminConstants.CODE_NOT_FOUND);
            }
            for (Account.AccountInfo info : response.getListList()) {
                accountMap.putIfAbsent(info.getGoodfishId(), info);
            }
            long fetched = (long) page * GoodfishAdminConstants.ACCOUNT_FETCH_PAGE_SIZE;
            if (response.getListList().isEmpty() || fetched >= response.getTotal()) {
                break;
            }
            page++;
        }
        return accountMap;
    }

    /**
     * 停用 / 启用账号，返回更新后的账号信息.
     */
    public Account.AccountInfo updateStatus(Long id, String status) {
        try {
            return accountStub.updateAccountStatus(Account.UpdateAccountStatusRequest.newBuilder()
                .setId(id == null ? 0L : id)
                .setStatus(status == null ? "" : status)
                .build());
        } catch (StatusRuntimeException e) {
            throw GrpcClientErrors.map(e, GoodfishAdminConstants.DESC_ACCOUNT_SERVICE,
                GoodfishAdminConstants.CODE_NOT_FOUND);
        }
    }

}
