package com.bigbear.aigc.biz;



import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author pig archetype
 * <p>
 * 项目启动类
 */

@EnableDiscoveryClient
@SpringBootApplication()
//@SpringBootApplication(exclude = {org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class})
//@SpringBootApplication(exclude = {dev.langchain4j.openai.spring.AutoConfig.class})
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }

}
