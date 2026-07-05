package com.bigbear.goodfish.domain.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户账户信息类，存储用户特定的连接和认证信息
 */
@EqualsAndHashCode(callSuper = false)
@Data
@Table("account")
public class AccountEntity {

    private String userId;

    private String deviceId;

    private String cookieStr;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.flexId)
    protected Long id;

    protected Date createTime;

    protected Date updateTime;
}
