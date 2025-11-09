package com.bigbear.goofish.core;

import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AccountService - 管理Account实例和ConnectionProperties的关联
 * 实现Account类和ConnectionProperties类的完全分离
 */
@Service
public class AccountService {

    // 系统配置
    @Getter
    private final ConnectionProperties systemProperties;

    // 用户账户映射表，使用userId作为key
    private final Map<String, Account> accountMap = new ConcurrentHashMap<>();

    @Autowired
    public AccountService(ConnectionProperties systemProperties) {
        this.systemProperties = systemProperties;
    }

    /**
     * 注册用户账户
     * @param account 用户账户实例
     */
    public void registerAccount(Account account) {
        if (account != null && account.getUserId() != null) {
            // todo 构建entity 再进行入库

            accountMap.put(account.getUserId(), account);
        }
    }

    /**
     * 获取用户账户
     * @param userId 用户ID
     * @return 用户账户实例
     */
    public Account getAccount(String userId) {
        return accountMap.get(userId);
    }

    /**
     * 移除用户账户
     * @param userId 用户ID
     */
    public void removeAccount(String userId) {
        if (userId != null) {
            accountMap.remove(userId);
        }
    }


    /**
     * 检查用户是否已登录
     * @param userId 用户ID
     * @return 是否已登录
     */
    public boolean isLoggedIn(String userId) {
        Account account = getAccount(userId);
        return account != null && account.getUserId() != null && account.getCookieStr() != null;
    }
}
