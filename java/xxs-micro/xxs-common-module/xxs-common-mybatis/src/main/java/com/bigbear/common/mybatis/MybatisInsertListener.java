package com.bigbear.common.mybatis;

import com.bigbear.common.core.BaseEntity;
import com.mybatisflex.annotation.InsertListener;
import java.time.LocalDateTime;

public class MybatisInsertListener implements InsertListener {

    @Override
    public void onInsert(Object o) {
        if(o instanceof BaseEntity entity) {
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
        }
    }
}
