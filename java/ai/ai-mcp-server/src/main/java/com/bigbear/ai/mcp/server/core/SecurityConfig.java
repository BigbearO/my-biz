package com.bigbear.ai.mcp.server.core;


import static org.springaicommunity.mcp.security.server.config.McpApiKeyConfigurer.mcpServerApiKey;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
//@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // 鉴权等认证参考：https://github.com/NingNing0111/spring-ai-mcp-security-server-demo
    //https://pgthinker.me/posts/spring-ai-mcp-security

//    private final McpApiKeyEntityRepository mcpApiKeyEntityRepository;
//
//    @Bean
//    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        return http
//            .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
//            .with(
//                mcpServerApiKey(),
//                (apiKey) -> apiKey.apiKeyRepository(mcpApiKeyEntityRepository)
//            )
//            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
//            .build();
//    }

}
