package org.dromara.goodfish.admin.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.goodfish.admin.service.IAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 授权校验（客户端启动时 + 运行中每小时调用）.
 *
 * <p>网关将 /system/auth/access 去除 /system 前缀后转发，故服务侧路径为 /auth/access.
 * 本期恒定返回 data=true；后续接入真实授权数据时契约不变.</p>
 *
 * @author goodfish
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthAccessController {

    private final IAuthService authService;

    /**
     * 授权校验.
     */
    @GetMapping("/access")
    public R<Boolean> access() {
        return R.ok(authService.hasAccess());
    }

}
