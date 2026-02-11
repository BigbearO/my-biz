package com.bigbear.mall.biz.manager.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.mall.biz.domain.entity.AccountEntity;
import com.bigbear.mall.biz.manager.IAccountManager;
import com.bigbear.mall.biz.mapper.AccountMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;

/**
 * 游戏账号基础服务实现
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Service
public class AccountManagerImpl extends ServiceImpl<AccountMapper, AccountEntity> implements IAccountManager {

    @Override
    public TableDataInfo<AccountEntity> selectPageList(AccountEntity config, PageQuery pageQuery) {
        LambdaQueryWrapper<AccountEntity> lqw = buildQueryWrapper(config);
        val result = this.baseMapper.selectPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    private LambdaQueryWrapper<AccountEntity> buildQueryWrapper(AccountEntity bo) {
        LambdaQueryWrapper<AccountEntity> lqw = Wrappers.lambdaQuery();

        lqw.eq(bo.getId() != null, AccountEntity::getId, bo.getId());
        lqw.eq(StringUtils.isNotBlank(bo.getUserAccount()), AccountEntity::getUserAccount, bo.getUserAccount());
        lqw.like(StringUtils.isNotBlank(bo.getUserAccount()), AccountEntity::getUserAccount, bo.getUserAccount());
        lqw.eq(bo.getType() != null, AccountEntity::getType, bo.getType());
        lqw.eq(bo.getEnabled() != null, AccountEntity::getEnabled, bo.getEnabled());
        lqw.eq(bo.getEmulatorId() != null, AccountEntity::getEmulatorId, bo.getEmulatorId());
        lqw.eq(bo.getTenantId() != null, AccountEntity::getTenantId, bo.getTenantId());

        // 时间范围查询
        if (bo.getStartTime() != null) {
            lqw.ge(AccountEntity::getStartTime, bo.getStartTime());
        }
        if (bo.getExpireTime() != null) {
            lqw.le(AccountEntity::getExpireTime, bo.getExpireTime());
        }

        lqw.orderByDesc(AccountEntity::getId);
        return lqw;
    }

}
