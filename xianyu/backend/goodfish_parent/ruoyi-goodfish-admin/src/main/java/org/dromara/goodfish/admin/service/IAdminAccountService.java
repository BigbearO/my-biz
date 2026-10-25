package org.dromara.goodfish.admin.service;

import org.dromara.goodfish.admin.domain.vo.AdminAccountVo;

/**
 * 管理后台账号Service接口（停用/启用转调）.
 *
 * @author goodfish
 */
public interface IAdminAccountService {

    /**
     * 停用 / 启用账号（转调客户端元数据服务）.
     *
     * <p>★账号只停用、不删除：停用不校验商品数、不级联删商品，
     * 已同步的商品全部保留（由客户端元数据服务保证）.</p>
     *
     * @param id     账号主键
     * @param status 目标状态（normal / disabled）
     * @return 更新后的账号信息（前端直接刷新行状态）
     */
    AdminAccountVo updateStatus(Long id, String status);

}
