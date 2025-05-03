package com.bigbear.order.infrastructure.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.order.infrastructure.entity.po.AccountPO;
import com.bigbear.order.mapper.AccountMapper;
import com.bigbear.order.infrastructure.repository.AccountManager;
import org.springframework.stereotype.Service;

/**
 * 游戏账号表
 *
 * @author bigbear
 * @date 2025-04-22 21:33:23
 */
@Service
public class AccountManagerImpl extends ServiceImpl<AccountMapper, AccountPO> implements AccountManager {

}
