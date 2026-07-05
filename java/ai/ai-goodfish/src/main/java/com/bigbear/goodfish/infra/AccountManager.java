package com.bigbear.goodfish.infra;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.bigbear.goodfish.domain.Account;
import com.bigbear.goodfish.domain.entity.AccountEntity;
import com.bigbear.goodfish.infra.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountManager  extends ServiceImpl<AccountMapper, AccountEntity> {

    public AccountEntity getByUserId(String userId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
            .from(AccountEntity.class)
            .where(AccountEntity::getUserId).eq(userId)
            .limit(1);
        return mapper.selectOneByQuery(queryWrapper);
    }


    @Transactional(rollbackFor = Exception.class)
    public void   registerAccount(Account account) {
        if (account != null && account.getUserId() != null) {
            AccountEntity entity = new AccountEntity();
            entity.setUserId(account.getUserId());
            entity.setDeviceId(account.getDeviceId());
            entity.setCookieStr(account.getCookieStr());
            mapper.insert(entity);
            return;
        }

        throw new RuntimeException("empty data");
    }

}
