package com.bigbear.mall.biz.mapper;

import com.bigbear.mall.biz.domain.entity.AccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 游戏账号Mapper
 */
@Mapper
public interface AccountMapper extends BaseMapperPlus<AccountEntity, AccountEntity> {

}
