package com.bigbear.spring.mybatis;


import com.bigbear.common.mybatis.FlexConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlexConfigAutoConfiguration {

    @PostConstruct
    public void init() {
        FlexConfig.registerListeners();
    }
}
