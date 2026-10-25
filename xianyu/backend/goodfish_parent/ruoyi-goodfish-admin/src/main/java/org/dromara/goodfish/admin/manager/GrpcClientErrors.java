package org.dromara.goodfish.admin.manager;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;

/**
 * gRPC 客户端异常映射（BFF 各客户端 manager 共用）.
 *
 * <p>NOT_FOUND：保留提供方描述并带 40401 业务码；其余状态（UNAVAILABLE / DEADLINE_EXCEEDED 等）
 * 统一转「xx服务暂时不可用」，便于前端提示重试.</p>
 *
 * @author goodfish
 */
@Slf4j
public final class GrpcClientErrors {

    private GrpcClientErrors() {
    }

    /**
     * 映射 gRPC 调用异常为业务异常.
     *
     * @param e             gRPC 状态异常
     * @param serviceDesc   服务描述（如「账号服务」「商品服务」）
     * @param notFoundCode  数据不存在业务码
     * @return 业务异常
     */
    public static ServiceException map(StatusRuntimeException e, String serviceDesc, int notFoundCode) {
        Status.Code code = e.getStatus().getCode();
        if (Status.Code.NOT_FOUND == code) {
            String description = e.getStatus().getDescription();
            return new ServiceException(StringUtils.isNotBlank(description) ? description : "数据不存在",
                notFoundCode);
        }
        log.warn("调用{} gRPC 失败: {}", serviceDesc, e.getStatus(), e);
        return new ServiceException(serviceDesc + "暂时不可用，请稍后重试");
    }

}
