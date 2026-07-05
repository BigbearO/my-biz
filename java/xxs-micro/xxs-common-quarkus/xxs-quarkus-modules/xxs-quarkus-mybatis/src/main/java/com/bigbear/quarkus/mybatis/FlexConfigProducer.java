package com.bigbear.quarkus.mybatis;

import com.bigbear.common.mybatis.FlexConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FlexConfigProducer {

    @PostConstruct
    public void init() {
        FlexConfig.registerListeners();
    }
}
