package com.bigbear.order.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.order.infrastructure.entity.po.AccountPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountMapper extends BaseMapper<AccountPO> {

}
