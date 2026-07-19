package com.bigbear.spring.grpc.greeter;

import com.bigbear.spring.grpc.greeter.GreeterGrpc.GreeterBlockingStub;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Greeter 充血客户端。
 *
 * <p>封装 gRPC 阻塞 Stub，向调用方暴露语义化方法，屏蔽 Protocol 细节与异常转译。
 * 由 {@link GreeterClientAutoConfiguration} 装配为 Spring Bean。
 */
public class GreeterClient {

    private static final Logger log = LoggerFactory.getLogger(GreeterClient.class);

    /** Greeter 阻塞 Stub（由 Spring gRPC 按通道 greeter 创建）。 */
    private final GreeterBlockingStub stub;

    /**
     * 构造 Greeter 充血客户端。
     *
     * @param stub Greeter 阻塞 Stub
     */
    public GreeterClient(GreeterBlockingStub stub) {
        this.stub = stub;
    }

    /**
     * 向 Greeter 服务发起问候。
     *
     * @param name 被问候者（允许为 null，按空串处理）
     * @return 问候回显消息
     * @throws StatusRuntimeException 调用失败（网络/服务不可用）时向上抛出
     */
    public String sayHello(String name) {
        try {
            return stub.sayHello(HelloRequest.newBuilder().setName(name == null ? "" : name).build()).getMessage();
        } catch (StatusRuntimeException e) {
            log.error("调用 Greeter.sayHello(name={}) 失败", name, e);
            throw e;
        }
    }
}
