package org.dromara.goodfish.meta.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.goodfish.meta.domain.GoodfishAccount;
import org.dromara.goodfish.meta.domain.vo.GoodfishAccountVo;
import org.dromara.goodfish.meta.mapper.GoodfishAccountMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 闲鱼账号原子操作层（manager）.
 *
 * <p>仅封装可复用的基础数据操作，不做业务编排（AGENTS.md 分层规范）.</p>
 *
 * @author goodfish
 */
@Component
@RequiredArgsConstructor
public class GoodfishAccountManager {

    private final GoodfishAccountMapper accountMapper;

    /**
     * 按 goodfish_id upsert：新账号插入完整记录；已存在更新昵称/备注/最后活跃时间，不更新 status.
     *
     * @param entity 账号实体（upsert 后 id 已回填）
     * @return 回填 id 的账号实体
     */
    public GoodfishAccount upsertByGoodfishId(GoodfishAccount entity) {
        accountMapper.upsertByGoodfishId(entity);
        return entity;
    }

    /**
     * 按主键查询视图对象.
     */
    public GoodfishAccountVo selectVoById(Long id) {
        return accountMapper.selectVoById(id);
    }

    /**
     * 按闲鱼用户 ID 查询视图对象.
     */
    public GoodfishAccountVo selectVoByGoodfishId(String goodfishId) {
        return accountMapper.selectVoOne(
            new LambdaQueryWrapper<GoodfishAccount>()
                .eq(GoodfishAccount::getGoodfishId, goodfishId));
    }

    /**
     * 分页查询账号视图对象.
     */
    public Page<GoodfishAccountVo> selectVoPage(Page<GoodfishAccount> page,
                                                String goodfishId, String goodfishNickname, String status) {
        LambdaQueryWrapper<GoodfishAccount> lqw = new LambdaQueryWrapper<>();
        lqw.like(StringUtils.isNotBlank(goodfishId), GoodfishAccount::getGoodfishId, goodfishId)
            .like(StringUtils.isNotBlank(goodfishNickname), GoodfishAccount::getGoodfishNickname, goodfishNickname)
            .eq(StringUtils.isNotBlank(status), GoodfishAccount::getStatus, status)
            .orderByDesc(GoodfishAccount::getId);
        return accountMapper.selectVoPage(page, lqw);
    }

    /**
     * 修改备注.
     */
    public boolean updateRemarkById(Long id, String remark) {
        GoodfishAccount update = new GoodfishAccount();
        update.setId(id);
        update.setRemark(remark);
        return accountMapper.updateById(update) > 0;
    }

    /**
     * 修改账号状态（启用 / 停用）.
     */
    public boolean updateStatusById(Long id, String status) {
        GoodfishAccount update = new GoodfishAccount();
        update.setId(id);
        update.setStatus(status);
        return accountMapper.updateById(update) > 0;
    }

    /**
     * 按主键集合查询（预留批量场景）.
     */
    public List<GoodfishAccountVo> selectVoByIds(List<Long> ids) {
        return accountMapper.selectVoByIds(ids);
    }

}
