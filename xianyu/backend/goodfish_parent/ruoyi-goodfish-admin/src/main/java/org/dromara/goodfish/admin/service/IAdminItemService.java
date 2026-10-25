package org.dromara.goodfish.admin.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.goodfish.admin.domain.vo.AdminItemVo;

/**
 * 管理后台商品列表Service接口（跨域聚合读）.
 *
 * @author goodfish
 */
public interface IAdminItemService {

    /**
     * 分页查询商品列表（gRPC 拉商品 + 拉账号，内存拼装账号名/昵称）.
     *
     * @param pageNum          页码（可空，默认 1）
     * @param pageSize         每页条数（可空，默认 10，上限 100）
     * @param goodfishAccountId 闲鱼账号 ID 筛选（可空）
     * @param title            标题模糊筛选（可空）
     */
    PageResult<AdminItemVo> queryPageList(Integer pageNum, Integer pageSize,
                                          String goodfishAccountId, String title);

}
