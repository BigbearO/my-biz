package com.bigbear.goodfish.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户账户信息类，存储用户特定的连接和认证信息
 */
@EqualsAndHashCode(callSuper = false)
@Data
@TableName("account")
public class AccountEntity {

    private String userId;

    private String deviceId;

    private String cookieStr;

    protected Long id;

    protected Date createTime;

    protected Date updateTime;
}
