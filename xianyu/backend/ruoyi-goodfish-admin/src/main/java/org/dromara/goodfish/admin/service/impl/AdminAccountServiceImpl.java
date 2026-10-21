package org.dromara.goodfish.admin.service.impl;

import account.v1.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.goodfish.admin.constants.GoodfishAdminConstants;
import org.dromara.goodfish.admin.domain.vo.AdminAccountVo;
import org.dromara.goodfish.admin.manager.AccountClientManager;
import org.dromara.goodfish.admin.service.IAdminAccountService;
import org.springframework.stereotype.Service;

/**
 * 管理后台账号Service业务层实现（停用/启用转调）.
 *
 * @author goodfish
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AdminAccountServiceImpl implements IAdminAccountService {

    private final AccountClientManager accountClientManager;

    /**
     * 停用 / 启用账号.
     */
    @Override
    public AdminAccountVo updateStatus(Long id, String status) {
        // 状态白名单校验（与客户端元数据服务同口径）
        if (!GoodfishAdminConstants.ACCOUNT_STATUS_NORMAL.equals(status)
            && !GoodfishAdminConstants.ACCOUNT_STATUS_DISABLED.equals(status)) {
            throw new ServiceException(GoodfishAdminConstants.MSG_INVALID_ACCOUNT_STATUS);
        }
        if (id == null) {
            throw new ServiceException(GoodfishAdminConstants.MSG_ACCOUNT_NOT_FOUND,
                GoodfishAdminConstants.CODE_NOT_FOUND);
        }

        log.info("[{}] 账号停用/启用转调 id={}, status={}",
            GoodfishAdminConstants.LOGGER_ADMIN_ACCOUNT_SERVICE, id, status);
        Account.AccountInfo account = accountClientManager.updateStatus(id, status);
        return toAdminAccountVo(account);
    }

    /**
     * 契约消息转后台 Vo.
     */
    private AdminAccountVo toAdminAccountVo(Account.AccountInfo account) {
        AdminAccountVo vo = new AdminAccountVo();
        vo.setId(account.getId());
        vo.setGoodfishId(account.getGoodfishId());
        vo.setGoodfishNickname(account.getGoodfishNickname());
        vo.setRemark(account.getRemark());
        vo.setStatus(account.getStatus());
        vo.setLastActiveAt(account.getLastActiveAt());
        return vo;
    }

}
