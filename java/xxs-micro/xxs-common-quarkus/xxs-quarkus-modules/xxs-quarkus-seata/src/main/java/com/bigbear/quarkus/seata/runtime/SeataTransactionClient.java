package com.bigbear.quarkus.seata.runtime;

import java.util.concurrent.TimeoutException;

import org.apache.seata.core.protocol.ResultCode;
import org.apache.seata.core.protocol.transaction.GlobalBeginRequest;
import org.apache.seata.core.protocol.transaction.GlobalBeginResponse;
import org.apache.seata.core.protocol.transaction.GlobalCommitRequest;
import org.apache.seata.core.protocol.transaction.GlobalCommitResponse;
import org.apache.seata.core.protocol.transaction.GlobalRollbackRequest;
import org.apache.seata.core.protocol.transaction.GlobalRollbackResponse;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Seata 事务协调客户端：以 TM 身份向 Transaction Coordinator（TC）发起全局事务的开启 / 提交 / 回滚。
 *
 * <p>供 Quarkus 端作为全局事务协调者（如 rollback-drill 演练、事务兜底补偿）时使用；作为普通分支参与方时
 * 无需调用本类。所有方法内部受 {@link SeataRuntime#isEnabled()} 门控，未启用时直接返回失败。
 *
 * @author xxs-micro
 */
@ApplicationScoped
public class SeataTransactionClient {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SeataTransactionClient.class);

    private final SeataRuntime seataRuntime;

    @Inject
    public SeataTransactionClient(SeataRuntime seataRuntime) {
        this.seataRuntime = seataRuntime;
    }

    /**
     * 开启全局事务。
     *
     * @param group 事务分组
     * @return 全局事务 XID
     */
    public String begin(String group) {
        if (!seataRuntime.isEnabled()) {
            return null;
        }
        GlobalBeginRequest request = new GlobalBeginRequest();
        try {
            GlobalBeginResponse response = (GlobalBeginResponse) client(group).sendSyncRequest(request);
            if (response != null && response.getResultCode() == ResultCode.Success) {
                return response.getXid();
            }
        } catch (TimeoutException e) {
            LOGGER.error("Seata global begin timeout", e);
        }
        return null;
    }

    /**
     * 提交指定全局事务（rollback-drill / 兜底提交）。
     *
     * @param xid 全局事务 XID
     * @return true 表示提交成功
     */
    public boolean commit(String xid) {
        if (!seataRuntime.isEnabled() || xid == null || xid.isBlank()) {
            return false;
        }
        GlobalCommitRequest request = new GlobalCommitRequest();
        request.setXid(xid);
        try {
            GlobalCommitResponse response = (GlobalCommitResponse) client(seataRuntime.getTxServiceGroup().orElse(null))
                    .sendSyncRequest(request);
            if (response != null && response.getResultCode() == ResultCode.Success) {
                LOGGER.info("Seata global commit ok, xid=[{}], status=[{}]", xid, response.getGlobalStatus());
                return true;
            }
        } catch (TimeoutException e) {
            LOGGER.error("Seata global commit timeout, xid=[{}]", xid, e);
        }
        return false;
    }

    /**
     * 回滚指定全局事务（rollback-drill / 兜底回滚）。
     *
     * @param xid 全局事务 XID
     * @return true 表示回滚成功
     */
    public boolean rollback(String xid) {
        if (!seataRuntime.isEnabled() || xid == null || xid.isBlank()) {
            return false;
        }
        GlobalRollbackRequest request = new GlobalRollbackRequest();
        request.setXid(xid);
        try {
            GlobalRollbackResponse response = (GlobalRollbackResponse) client(seataRuntime.getTxServiceGroup().orElse(null))
                    .sendSyncRequest(request);
            if (response != null && response.getResultCode() == ResultCode.Success) {
                LOGGER.info("Seata global rollback ok, xid=[{}], status=[{}]", xid, response.getGlobalStatus());
                return true;
            }
        } catch (TimeoutException e) {
            LOGGER.error("Seata global rollback timeout, xid=[{}]", xid, e);
        }
        return false;
    }

    /**
     * 获取 TM 客户端单例。
     *
     * @param group 事务分组
     * @return TM 客户端
     */
    private TmNettyRemotingClient client(String group) {
        return TmNettyRemotingClient.getInstance("quarkus-app", group);
    }
}
