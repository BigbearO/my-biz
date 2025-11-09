package com.bigbear.goofish.infra;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.goofish.core.Account;
import com.bigbear.goofish.domain.entity.AccountEntity;
import com.bigbear.goofish.infra.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountManager  extends ServiceImpl<AccountMapper, AccountEntity> {

    public AccountEntity getByUserId(String userId) {
        // 构建lambda查询
        return lambdaQuery().eq(AccountEntity::getUserId, userId).last(" limit 1").one();
    }


    @Transactional(rollbackFor = Exception.class)
    public void   registerAccount(Account account) {
        if (account != null && account.getUserId() != null) {
            // todo 构建entity 再进行入库
            AccountEntity entity = new AccountEntity();
            entity.setUserId(account.getUserId());
            entity.setDeviceId(account.getDeviceId());
            this.save(entity);

        }

        throw new RuntimeException("empty data");
    }

}
