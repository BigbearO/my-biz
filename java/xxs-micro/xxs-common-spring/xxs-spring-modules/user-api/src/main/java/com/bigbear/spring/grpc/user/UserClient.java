package com.bigbear.spring.grpc.user;

import com.bigbear.spring.grpc.user.UserServiceGrpc.UserServiceBlockingStub;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User 充血客户端。
 *
 * <p>封装 gRPC 阻塞 Stub，向调用方暴露语义化方法，屏蔽 Protocol 细节与异常转译。
 * 由 {@link UserClientAutoConfiguration} 装配为 Spring Bean。
 */
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    /** User 阻塞 Stub（由 Spring gRPC 按通道 user 创建）。 */
    private final UserServiceBlockingStub stub;

    /**
     * 构造 User 充血客户端。
     *
     * @param stub User 阻塞 Stub
     */
    public UserClient(UserServiceBlockingStub stub) {
        this.stub = stub;
    }

    /**
     * 按用户标识查询用户基本信息。
     *
     * @param id 用户唯一标识（允许为 null，按空串处理）
     * @return User 服务返回的用户信息（含 id/name/age）
     * @throws StatusRuntimeException 调用失败（网络/服务不可用）时向上抛出
     */
    public GetUserReply getUser(String id) {
        try {
            return stub.getUser(GetUserRequest.newBuilder().setId(id == null ? "" : id).build());
        } catch (StatusRuntimeException e) {
            log.error("调用 User.getUser(id={}) 失败", id, e);
            throw e;
        }
    }
}
