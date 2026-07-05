package com.bigbear.goodfish.core;

import cn.hutool.json.JSONUtil;
import com.bigbear.goodfish.domain.Account;
import com.bigbear.goodfish.domain.bo.AccountBo;
import com.bigbear.goodfish.domain.entity.AccountEntity;
import com.bigbear.goodfish.infra.AccountManager;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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
        QueryWrapper qw = buildQueryWrapper(bo);
        Page<AccountEntity> result = accountManager.page(Page.of(pageQuery.getPageNum(), pageQuery.getPageSize()), qw);
        TableDataInfo<AccountEntity> dataInfo = new TableDataInfo<>();
        dataInfo.setRows(result.getRecords());
        dataInfo.setTotal(result.getTotalRow());
        dataInfo.setCode(200);
        dataInfo.setMsg("查询成功");
        return dataInfo;
    }


    private QueryWrapper buildQueryWrapper(AccountBo bo) {
        QueryWrapper qw = QueryWrapper.create().from(AccountEntity.class);
        qw.orderBy(AccountEntity::getId, true);
        if (StringUtils.isNotBlank(bo.getUserId())) {
            qw.and(AccountEntity::getUserId).eq(bo.getUserId());
        }
        if (StringUtils.isNotBlank(bo.getDeviceId())) {
            qw.and(AccountEntity::getDeviceId).eq(bo.getDeviceId());
        }
        return qw;
    }


    /**
     * 注册用户账户
     *
     * @param account 用户账户实例
     */
    public void registerAccount(Account account) {
        accountManager.registerAccount(account);
        addAccCache(account);
    }

    public void addAccCache(Account account) {
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
