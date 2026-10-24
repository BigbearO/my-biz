package org.dromara.goodfish.meta.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.goodfish.meta.domain.bo.GoodfishAccountBo;
import org.dromara.goodfish.meta.domain.bo.GoodfishAccountReportBo;
import org.dromara.goodfish.meta.domain.vo.GoodfishAccountVo;

/**
 * 闲鱼账号Service接口（账号域业务编排）.
 *
 * @author goodfish
 */
public interface IGoodfishAccountService {

    /**
     * 客户端账号上报：按 goodfish_id 查，没有则新增、有则按主键更新.
     *
     * <p>已存在仅更新昵称/备注/最后活跃时间，<strong>不停用复活</strong>；不接收 Cookie.</p>
     *
     * @param bo 上报参数（goodfish_id / goodfish_nickname / remark）
     * @return 上报后的账号信息
     */
    GoodfishAccountVo reportAccount(GoodfishAccountReportBo bo);

    /**
     * 分页查询账号列表（后台）.
     */
    PageResult<GoodfishAccountVo> queryPageList(GoodfishAccountBo bo, PageQuery pageQuery);

    /**
     * 查询账号详情（后台）.
     *
     * @throws org.dromara.common.core.exception.ServiceException 账号不存在（40401）
     */
    GoodfishAccountVo queryById(Long id);

    /**
     * 修改账号备注（后台）.
     */
    Boolean updateRemark(GoodfishAccountBo bo);

    /**
     * 启用 / 停用账号（后台）.
     *
     * <p>★只停用、不删除：不校验商品数、不级联删商品.</p>
     *
     * @param id     账号主键
     * @param status 目标状态（normal / disabled）
     */
    Boolean updateStatus(Long id, String status);

    /**
     * 按闲鱼用户 ID 查询账号（gRPC 供商品同步解析 account_id）.
     *
     * @return 账号信息，不存在返回 null
     */
    GoodfishAccountVo getByGoodfishId(String goodfishId);

}
