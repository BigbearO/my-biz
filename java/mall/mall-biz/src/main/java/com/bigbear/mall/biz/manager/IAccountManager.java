package com.bigbear.mall.biz.manager;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bigbear.mall.api.common.constants.Games;
import com.bigbear.mall.biz.domain.entity.AccountEntity;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

/**
 * 游戏账号基础服务接口
 */
public interface IAccountManager extends IService<AccountEntity> {

    /**
     * 根据账号获取或创建账号信息
     * TODO: 加锁保证线程安全
     */
    default AccountEntity getByAccount(String userAccount) {
        AccountEntity entity = lambdaQuery()
                .eq(AccountEntity::getUserAccount, userAccount)
                .one();

        if (entity != null) {
            return entity;
        }

        entity = new AccountEntity();
        entity.setExpireTime(Games.DEFAULT_ACCOUNT_TIME)
                .setUserAccount(userAccount);
        this.save(entity);
        return entity;
    }


    TableDataInfo<AccountEntity> selectPageList(AccountEntity config, PageQuery pageQuery);
}
