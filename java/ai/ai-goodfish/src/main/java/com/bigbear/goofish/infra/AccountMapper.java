package com.bigbear.goofish.infra;

import com.bigbear.ai.biz.domain.entity.ItemEntity;
import com.bigbear.goofish.domain.entity.AccountEntity;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.annotations.Mapper;


/**
 * 商品Mapper
 *
 * @author autoagent
 */
@Mapper
public interface AccountMapper extends MPJBaseMapper<AccountEntity>{

}
