package org.dromara.goodfish.meta.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.goodfish.meta.constants.GoodfishMetaConstants;
import org.dromara.goodfish.meta.domain.GoodfishAccount;
import org.dromara.goodfish.meta.domain.bo.GoodfishAccountBo;
import org.dromara.goodfish.meta.domain.bo.GoodfishAccountReportBo;
import org.dromara.goodfish.meta.domain.vo.GoodfishAccountVo;
import org.dromara.goodfish.meta.manager.GoodfishAccountManager;
import org.dromara.goodfish.meta.service.IGoodfishAccountService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 闲鱼账号Service业务层实现（业务编排，不直接操作数据库）.
 *
 * @author goodfish
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GoodfishAccountServiceImpl implements IGoodfishAccountService {

    private final GoodfishAccountManager accountManager;

    /**
     * 客户端账号上报（不接收 Cookie）.
     *
     * <p>语义显式化：先按 goodfish_id 查，<b>没有就插入</b>、<b>有就按主键更新</b>。
     * 新账号才落 status=normal；已存在账号的启停由管理员控制，上报不改 status（★不停用复活）.</p>
     */
    @Override
    public GoodfishAccountVo reportAccount(GoodfishAccountReportBo bo) {
        GoodfishAccountVo existing = accountManager.selectVoByGoodfishId(bo.getGoodfishId());
        GoodfishAccountVo saved = existing == null ? register(bo) : touch(existing.getId(), bo);
        log.info("[{}] 账号上报完成 goodfishId={}, id={}, 动作={}",
            GoodfishMetaConstants.LOGGER_ACCOUNT_SERVICE, saved.getGoodfishId(), saved.getId(),
            existing == null ? "新增" : "更新");
        return saved;
    }

    /**
     * 新账号入库：status 只在新增时写 normal.
     *
     * <p>并发兜底：两台设备同时上报同一个新账号时，唯一索引 uk_goodfish_account_goodfish_id
     * 会让后到的一方抛 DuplicateKeyException，此时退化为按主键更新，不向上抛错.</p>
     */
    private GoodfishAccountVo register(GoodfishAccountReportBo bo) {
        GoodfishAccount entity = MapstructUtils.convert(bo, GoodfishAccount.class);
        entity.setStatus(GoodfishMetaConstants.ACCOUNT_STATUS_NORMAL);
        entity.setLastActiveAt(LocalDateTime.now());
        try {
            accountManager.insert(entity);
        } catch (DuplicateKeyException e) {
            GoodfishAccount concurrent = accountManager.selectByGoodfishId(bo.getGoodfishId());
            if (concurrent == null) {
                throw e;
            }
            return touch(concurrent.getId(), bo);
        }
        return MapstructUtils.convert(entity, GoodfishAccountVo.class);
    }

    /**
     * 已存在账号：只更新昵称 / 备注 / 最后活跃时间，不碰 status.
     */
    private GoodfishAccountVo touch(Long id, GoodfishAccountReportBo bo) {
        GoodfishAccount update = new GoodfishAccount();
        update.setId(id);
        update.setGoodfishNickname(bo.getGoodfishNickname());
        update.setRemark(bo.getRemark());
        update.setLastActiveAt(LocalDateTime.now());
        accountManager.updateById(update);
        return accountManager.selectVoById(id);
    }

    /**
     * 分页查询账号列表.
     */
    @Override
    public PageResult<GoodfishAccountVo> queryPageList(GoodfishAccountBo bo, PageQuery pageQuery) {
        Page<GoodfishAccountVo> page = accountManager.selectVoPage(pageQuery.build(),
            bo.getGoodfishId(), bo.getGoodfishNickname(), bo.getStatus());
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 查询账号详情.
     */
    @Override
    public GoodfishAccountVo queryById(Long id) {
        GoodfishAccountVo vo = accountManager.selectVoById(id);
        if (vo == null) {
            throw new ServiceException(GoodfishMetaConstants.MSG_ACCOUNT_NOT_FOUND,
                GoodfishMetaConstants.CODE_ACCOUNT_NOT_FOUND);
        }
        return vo;
    }

    /**
     * 修改账号备注.
     */
    @Override
    public Boolean updateRemark(GoodfishAccountBo bo) {
        this.queryById(bo.getId());
        return accountManager.updateRemarkById(bo.getId(), bo.getRemark());
    }

    /**
     * 启用 / 停用账号（只停用、不删除，不校验商品数、不级联删商品）.
     */
    @Override
    public Boolean updateStatus(Long id, String status) {
        if (!GoodfishMetaConstants.ACCOUNT_STATUS_NORMAL.equals(status)
            && !GoodfishMetaConstants.ACCOUNT_STATUS_DISABLED.equals(status)) {
            throw new ServiceException("账号状态仅支持 normal / disabled");
        }
        this.queryById(id);
        return accountManager.updateStatusById(id, status);
    }

    /**
     * 按闲鱼用户 ID 查询账号（gRPC）.
     */
    @Override
    public GoodfishAccountVo getByGoodfishId(String goodfishId) {
        if (StringUtils.isBlank(goodfishId)) {
            return null;
        }
        return accountManager.selectVoByGoodfishId(goodfishId);
    }

}
