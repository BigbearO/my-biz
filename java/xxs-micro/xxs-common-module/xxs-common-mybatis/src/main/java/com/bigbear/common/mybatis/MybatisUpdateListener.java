package com.bigbear.common.mybatis;

import com.bigbear.common.core.BaseEntity;
import com.mybatisflex.annotation.UpdateListener;
import java.time.LocalDateTime;

public class MybatisUpdateListener implements UpdateListener {

    @Override
    public void onUpdate(Object o) {
        if(o instanceof BaseEntity entity) {
            entity.setUpdateTime(LocalDateTime.now());
        }
    }
}
