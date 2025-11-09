package com.bigbear.goofish.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.goofish.domain.entity.AccountEntity;
import org.apache.ibatis.annotations.Mapper;


/**
 * 商品Mapper
 *
 * @author autoagent
 */
@Mapper
public interface AccountMapper extends BaseMapper<AccountEntity> {

}
