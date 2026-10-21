package org.dromara.goodfish.meta.grpc;

import account.v1.Account;
import account.v1.AccountServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.goodfish.meta.constants.GoodfishMetaConstants;
import org.dromara.goodfish.meta.domain.bo.GoodfishAccountBo;
import org.dromara.goodfish.meta.domain.bo.GoodfishAccountReportBo;
import org.dromara.goodfish.meta.domain.vo.GoodfishAccountVo;
import org.dromara.goodfish.meta.service.IGoodfishAccountService;
import org.springframework.grpc.server.service.GrpcService;

/**
 * 账号域 gRPC 服务实现（AccountService）.
 *
 * <p>由 Spring Boot 4.1 官方 spring-grpc 的 {@link GrpcService} 自动注册；
 * 监听端口经 xxs-spring-grpc-nacos 以 {@code <appName>-grpc} 双注册到 Nacos.</p>
 *
 * @author goodfish
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {

    private final IGoodfishAccountService accountService;

    /**
     * 账号上报 upsert（REST report 的服务间等价入口）.
     */
    @Override
    public void upsertAccount(Account.UpsertAccountRequest request,
                              StreamObserver<Account.AccountInfo> responseObserver) {
        try {
            GoodfishAccountReportBo bo = new GoodfishAccountReportBo();
            bo.setGoodfishId(request.getGoodfishId());
            bo.setGoodfishNickname(request.getGoodfishNickname());
            bo.setRemark(request.getRemark());
            GoodfishAccountVo vo = accountService.reportAccount(bo);
            responseObserver.onNext(toAccountInfo(vo));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[{}] upsertAccount 失败 goodfishId={}",
                GoodfishMetaConstants.LOGGER_ACCOUNT_GRPC, request.getGoodfishId(), e);
            responseObserver.onError(toStatus(e).asRuntimeException());
        }
    }

    /**
     * 按主键查询账号.
     */
    @Override
    public void getAccount(Account.GetAccountRequest request,
                           StreamObserver<Account.AccountInfo> responseObserver) {
        try {
            GoodfishAccountVo vo = accountService.queryById(request.getId());
            responseObserver.onNext(toAccountInfo(vo));
            responseObserver.onCompleted();
        } catch (ServiceException e) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("[{}] getAccount 失败 id={}",
                GoodfishMetaConstants.LOGGER_ACCOUNT_GRPC, request.getId(), e);
            responseObserver.onError(toStatus(e).asRuntimeException());
        }
    }

    /**
     * 按闲鱼用户 ID 查询账号（商品同步落库时解析 account_id 用）.
     *
     * <p>停用账号同样正常返回：已同步商品与 AI 回复链路不受影响，
     * 停用仅拦截商品同步入口（由商品服务返回 40404）.</p>
     */
    @Override
    public void getAccountByGoodfishId(Account.GetAccountByGoodfishIdRequest request,
                                       StreamObserver<Account.AccountInfo> responseObserver) {
        try {
            GoodfishAccountVo vo = accountService.getByGoodfishId(request.getGoodfishId());
            if (vo == null) {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription(GoodfishMetaConstants.MSG_ACCOUNT_NOT_FOUND).asRuntimeException());
                return;
            }
            responseObserver.onNext(toAccountInfo(vo));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[{}] getAccountByGoodfishId 失败 goodfishId={}",
                GoodfishMetaConstants.LOGGER_ACCOUNT_GRPC, request.getGoodfishId(), e);
            responseObserver.onError(toStatus(e).asRuntimeException());
        }
    }

    /**
     * 停用/启用账号（admin BFF 转调；★只停用、不删除，停用不级联删商品）.
     */
    @Override
    public void updateAccountStatus(Account.UpdateAccountStatusRequest request,
                                    StreamObserver<Account.AccountInfo> responseObserver) {
        try {
            accountService.updateStatus(request.getId(), request.getStatus());
            // 返回更新后的账号信息（前端直接刷新行状态）
            GoodfishAccountVo vo = accountService.queryById(request.getId());
            responseObserver.onNext(toAccountInfo(vo));
            responseObserver.onCompleted();
        } catch (ServiceException e) {
            if (java.util.Objects.equals(e.getCode(), GoodfishMetaConstants.CODE_ACCOUNT_NOT_FOUND)) {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage()).asRuntimeException());
            }
        } catch (Exception e) {
            log.error("[{}] updateAccountStatus 失败 id={}, status={}",
                GoodfishMetaConstants.LOGGER_ACCOUNT_GRPC, request.getId(), request.getStatus(), e);
            responseObserver.onError(toStatus(e).asRuntimeException());
        }
    }

    /**
     * 分页查询账号列表.
     */
    @Override
    public void listAccounts(Account.ListAccountsRequest request,
                             StreamObserver<Account.ListAccountsResponse> responseObserver) {
        try {
            int pageNum = request.getPage() > 0 ? request.getPage() : PageQuery.DEFAULT_PAGE_NUM;
            int pageSize = resolvePageSize(request.getPageSize());
            PageResult<GoodfishAccountVo> result =
                accountService.queryPageList(new GoodfishAccountBo(), new PageQuery(pageSize, pageNum));

            Account.ListAccountsResponse.Builder builder = Account.ListAccountsResponse.newBuilder()
                .setTotal(result.getTotal());
            for (GoodfishAccountVo vo : result.getRows()) {
                builder.addList(toAccountInfo(vo));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[{}] listAccounts 失败", GoodfishMetaConstants.LOGGER_ACCOUNT_GRPC, e);
            responseObserver.onError(toStatus(e).asRuntimeException());
        }
    }

    /**
     * 分页大小归一化：未传/非法取默认值，超过上限截断.
     */
    private int resolvePageSize(int requestPageSize) {
        if (requestPageSize <= 0) {
            return GoodfishMetaConstants.GRPC_DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestPageSize, GoodfishMetaConstants.GRPC_MAX_PAGE_SIZE);
    }

    /**
     * 实体视图对象转契约消息.
     */
    private Account.AccountInfo toAccountInfo(GoodfishAccountVo vo) {
        Account.AccountInfo.Builder builder = Account.AccountInfo.newBuilder()
            .setId(vo.getId() == null ? 0L : vo.getId())
            .setGoodfishId(nullToEmpty(vo.getGoodfishId()))
            .setGoodfishNickname(nullToEmpty(vo.getGoodfishNickname()))
            .setRemark(nullToEmpty(vo.getRemark()))
            // 账号状态：商品同步据此校验停用（40404）
            .setStatus(nullToEmpty(vo.getStatus()));
        if (vo.getLastActiveAt() != null) {
            builder.setLastActiveAt(vo.getLastActiveAt().format(GoodfishMetaConstants.DATE_TIME_FORMATTER));
        }
        return builder.build();
    }

    /**
     * proto3 字符串字段不接受 null，统一转空串.
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 业务异常映射为 gRPC 状态（携带原始 message 便于调用方定位）.
     */
    private Status toStatus(Exception e) {
        return Status.INTERNAL.withCause(e).withDescription(e.getMessage());
    }

}
