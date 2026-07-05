package com.bigbear.goodfish.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Data
@Configuration
@ConfigurationProperties("goodfish")
public class ConnectionProperties {

    // 默认配置值 - 仅保留系统级配置，不包含用户特定信息
    private String apiUrl = "https://h5api.m.goofish.com/h5/";
    private String tokenUri = "mtop.taobao.idlemessage.pc.login.token/1.0/";
    private String iteminfoUri = "mtop.taobao.idle.pc.detail/1.0/";
    private URI socketUrl = URI.create("wss://wss-goofish.dingtalk.com/");
    private String httpsUrl = "https://www.goofish.com";
    private String loginCheckUrl = "";
    private String uploadUrl = "";
    private long interval = 15L;
    private int timeout = 30;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";


}
