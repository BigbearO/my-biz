package com.bigbear.ai.ai3d.core.config;

import com.bigbear.common.core.BaseEntity;
import com.mybatisflex.annotation.InsertListener;
import com.mybatisflex.annotation.UpdateListener;
import com.mybatisflex.core.FlexGlobalConfig;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlexConfig   {

    @PostConstruct
    public void initFlexGlobalConfig() {
        FlexGlobalConfig defaultConfig = FlexGlobalConfig.getDefaultConfig();

        MybatisInsertListener mybatisInsertListener = new MybatisInsertListener();
        MybatisUpdateListener mybatisUpdateListener = new MybatisUpdateListener();

        defaultConfig.registerInsertListener(mybatisInsertListener, BaseEntity.class);
        defaultConfig.registerUpdateListener(mybatisUpdateListener, BaseEntity.class);
    }


    public static class MybatisInsertListener implements InsertListener {

        @Override
        public void onInsert(Object o) {
            if(o instanceof BaseEntity entity) {
                entity.setCreateTime(LocalDateTime.now());
                entity.setUpdateTime(LocalDateTime.now());
            }
        }
    }

    public static class MybatisUpdateListener implements UpdateListener {

        @Override
        public void onUpdate(Object o) {
            if(o instanceof BaseEntity entity) {
                entity.setUpdateTime(LocalDateTime.now());
            }
        }
    }
}
