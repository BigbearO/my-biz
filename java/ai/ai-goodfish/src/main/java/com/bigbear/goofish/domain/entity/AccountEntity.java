package com.bigbear.goofish.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bigbear.common.core.BaseEntity;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.DigestUtils;

/**
 * 用户账户信息类，存储用户特定的连接和认证信息
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("account")
public class AccountEntity extends BaseEntity {

    private String userId;

    private String deviceId;

}
