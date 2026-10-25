package org.dromara.goodfish.admin.service;

/**
 * 授权校验Service接口（客户端启动时 + 运行中每小时调用）.
 *
 * @author goodfish
 */
public interface IAuthService {

    /**
     * 授权校验：本期恒定返回 {@code true}.
     *
     * <p>后续接入真实授权数据（到期时间 / 授权类型）时在此编排，接口契约不变.</p>
     */
    boolean hasAccess();

}
