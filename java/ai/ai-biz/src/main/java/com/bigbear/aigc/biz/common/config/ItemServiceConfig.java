package com.bigbear.aigc.biz.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品服务配置类
 */
@Configuration
public class ItemServiceConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}