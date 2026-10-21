package org.dromara.goodfish.admin.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.dromara.goodfish.admin.constants.GoodfishAdminConstants;
import org.dromara.goodfish.admin.service.IAuthService;
import org.springframework.stereotype.Service;

/**
 * 授权校验Service业务层实现.
 *
 * <p>本期恒定返回 true，不查任何下游；后续接入真实授权数据（到期时间 / 授权类型）时
 * 在此编排（如调用客户端元数据服务或授权数据源），接口契约不变.</p>
 *
 * @author goodfish
 */
@Slf4j
@Service
public class AuthServiceImpl implements IAuthService {

    /**
     * 授权校验：恒 true.
     */
    @Override
    public boolean hasAccess() {
        log.debug("[{}] 授权校验：本期恒定放行", GoodfishAdminConstants.LOGGER_AUTH_SERVICE);
        return true;
    }

}
