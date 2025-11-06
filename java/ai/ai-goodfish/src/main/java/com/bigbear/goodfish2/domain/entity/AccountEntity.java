package com.bigbear.goodfish2.domain.entity;

import com.bigbear.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AccountEntity extends BaseEntity {

    private String cookieStr;

    private String userId;

}
