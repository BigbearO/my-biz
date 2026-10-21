package org.dromara.goodfish.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.goodfish.admin.domain.bo.AdminAccountStatusBo;
import org.dromara.goodfish.admin.domain.vo.AdminAccountVo;
import org.dromara.goodfish.admin.service.IAdminAccountService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台账号停用/启用（BFF 转调客户端元数据服务）.
 *
 * <p>★账号只停用、不删除：停用不校验商品数、不级联删商品.</p>
 *
 * @author goodfish
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/admin/account")
public class AdminAccountController extends BaseController {

    private final IAdminAccountService adminAccountService;

    /**
     * 停用 / 启用账号.
     */
    @PutMapping("/{id}/status")
    public R<AdminAccountVo> changeStatus(@PathVariable Long id,
                                          @Valid @RequestBody AdminAccountStatusBo bo) {
        return R.ok(adminAccountService.updateStatus(id, bo.getStatus()));
    }

}
