package com.bigbear.common.mybatis;

import com.bigbear.common.core.BaseEntity;
import com.mybatisflex.core.FlexGlobalConfig;

public class FlexConfig {

    public static void registerListeners() {
        FlexGlobalConfig defaultConfig = FlexGlobalConfig.getDefaultConfig();
        defaultConfig.registerInsertListener(new MybatisInsertListener(), BaseEntity.class);
        defaultConfig.registerUpdateListener(new MybatisUpdateListener(), BaseEntity.class);
    }
}
