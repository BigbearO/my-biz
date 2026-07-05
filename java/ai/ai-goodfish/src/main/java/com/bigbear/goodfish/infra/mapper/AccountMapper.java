package com.bigbear.goodfish.infra.mapper;

import com.mybatisflex.core.BaseMapper;
import com.bigbear.goodfish.domain.entity.AccountEntity;
import org.apache.ibatis.annotations.Mapper;


/**
 * 商品Mapper
 *
 * @author autoagent
 */
@Mapper
public interface AccountMapper extends BaseMapper<AccountEntity> {

}
