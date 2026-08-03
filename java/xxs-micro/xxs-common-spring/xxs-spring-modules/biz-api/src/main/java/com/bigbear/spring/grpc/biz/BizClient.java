package com.bigbear.spring.grpc.biz;

import com.bigbear.spring.grpc.biz.BizServiceGrpc.BizServiceBlockingStub;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Biz 充血客户端。
 *
 * <p>封装 gRPC 阻塞 Stub，向调用方暴露语义化方法，屏蔽 Protocol 细节与异常转译。
 * 对接 Quarkus 侧 {@code BizServiceImpl}（XA 分支写库），参与跨语言全局事务。
 * 由 {@link BizClientAutoConfiguration} 装配为 Spring Bean。
 */
public class BizClient {

    private static final Logger log = LoggerFactory.getLogger(BizClient.class);

    /** Biz 阻塞 Stub（由 Spring gRPC 按通道 quarkus 创建）。 */
    private final BizServiceBlockingStub stub;

    /**
     * 构造 Biz 充血客户端。
     *
     * @param stub Biz 阻塞 Stub
     */
    public BizClient(BizServiceBlockingStub stub) {
        this.stub = stub;
    }

    /**
     * 创建业务记录（参与全局事务的跨端写操作，由 Quarkus 分支落库）。
     *
     * @param name 业务名称
     * @param val  业务数值
     * @return Biz 服务返回的创建结果
     * @throws StatusRuntimeException 调用失败时向上抛出，供上游全局事务回滚
     */
    public CreateBizReply createBiz(String name, int val) {
        try {
            return stub.createBiz(CreateBizRequest.newBuilder().setName(name).setVal(val).build());
        } catch (StatusRuntimeException e) {
            log.error("调用 Biz.createBiz(name={}, val={}) 失败", name, val, e);
            throw e;
        }
    }
}
