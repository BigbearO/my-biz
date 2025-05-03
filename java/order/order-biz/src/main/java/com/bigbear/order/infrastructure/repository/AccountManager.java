package com.bigbear.order.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bigbear.order.infrastructure.entity.po.AccountPO;
import com.xxs.order.api.common.constants.Games;

public interface AccountManager extends IService<AccountPO> {

//    todo 加锁线程安全
    default AccountPO getByAccount(String userAccount){
        LambdaQueryWrapper<AccountPO> queryWrapper= Wrappers.lambdaQuery();
        queryWrapper.eq(AccountPO::getUserAccount,userAccount);

        AccountPO one = this.getOne(queryWrapper);
        if(one!=null){
            return one;
        }
        one=new AccountPO();
        one.setExpireTime(Games.DEFAULT_ACCOUNT_TIME)
            .setExpireTime(Games.DEFAULT_ACCOUNT_TIME)
            .setUserAccount(userAccount)
            ;
        this.save(one);
        return one;

    }

}
