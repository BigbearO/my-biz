package com.bigbear.aigc.ai.common.config;

import com.bigbear.aigc.ai.service.CustomerService;
import java.util.List;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiMCPConfig {

    //ToolCallbacks.from() 方法会自动查找并注册服务中所有带有 @Tool 注解的方法。
    @Bean
    public List<ToolCallback> crmTools(CustomerService customerService) {
        return List.of(ToolCallbacks.from(customerService));
    }
}
