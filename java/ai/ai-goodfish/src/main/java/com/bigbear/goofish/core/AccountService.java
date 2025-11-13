package com.bigbear.goofish.core;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bigbear.goofish.domain.Account;
import com.bigbear.goofish.domain.bo.AccountBo;
import com.bigbear.goofish.domain.entity.AccountEntity;
import com.bigbear.goofish.infra.AccountManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AccountService - 管理Account实例和ConnectionProperties的关联 实现Account类和ConnectionProperties类的完全分离
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    // 系统配置
    @Getter
    private final ConnectionProperties systemProperties;

    private final AccountManager accountManager;

    // 用户账户映射，使用userId作为key
    private final RedisTemplate<String, Object> redisTemplate;


    public TableDataInfo<AccountEntity> queryPageList(AccountBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AccountEntity> lqw = buildQueryWrapper(bo);

        Page<AccountEntity> build = pageQuery.build();
        Page<AccountEntity> result = accountManager.page(build, lqw);

        return TableDataInfo.build(result);
    }


    private LambdaQueryWrapper<AccountEntity> buildQueryWrapper(AccountBo bo) {
        LambdaQueryWrapper<AccountEntity> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(AccountEntity::getId);
        lqw.eq(StringUtils.isNotBlank(bo.getUserId()), AccountEntity::getUserId, bo.getUserId());
        lqw.eq(StringUtils.isNotBlank(bo.getDeviceId()), AccountEntity::getDeviceId,
            bo.getDeviceId());
        return lqw;
    }


    /**
     * 注册用户账户
     *
     * @param account 用户账户实例
     */
    public void registerAccount(Account account) {
        accountManager.registerAccount(account);

        redisTemplate.opsForValue().set(getAccountInfoKey(account.getUserId()), account);

    }

    private String getAccountInfoKey(String  userId) {

        return "USER:"+userId;
    }
    /**
     * 获取用户账户
     *
     * @param userId 用户ID
     * @return 用户账户实例
     */
    public Account getAccount(String userId) {
        Object o = redisTemplate.opsForValue().get(getAccountInfoKey(userId));
        if(o==null){
            return  null;
        }

        return  JSONUtil.toBean(JSONUtil.toJsonStr(o), Account.class);
    }

    /**
     * 移除用户账户
     *
     * @param userId 用户ID
     */
    public void removeAccount(String userId) {
        if (userId != null) {
            redisTemplate.delete(getAccountInfoKey(userId));
        }
    }


    /**
     * 检查用户是否已登录
     *
     * @param userId 用户ID
     * @return 是否已登录
     */
    public boolean isLoggedIn(String userId) {
        Account account = getAccount(userId);
        return account != null && account.getUserId() != null && account.getCookieStr() != null;
    }
}
