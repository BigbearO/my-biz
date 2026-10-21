package org.dromara.goodfish.meta.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.goodfish.meta.domain.GoodfishAccount;
import org.dromara.goodfish.meta.domain.vo.GoodfishAccountVo;

/**
 * 闲鱼账号Mapper接口.
 *
 * @author goodfish
 */
public interface GoodfishAccountMapper extends BaseMapperPlus<GoodfishAccount, GoodfishAccountVo> {

    /**
     * 按 goodfish_id upsert（PostgreSQL ON CONFLICT 原子操作）.
     *
     * <p>新账号插入完整记录；已存在仅更新昵称/备注/最后活跃时间，
     * <strong>不更新 status</strong>（★上报不停用复活，停用由管理员控制）.</p>
     *
     * @param entity 账号实体（id 由 useGeneratedKeys 回填）
     * @return 影响行数
     */
    int upsertByGoodfishId(GoodfishAccount entity);

}
