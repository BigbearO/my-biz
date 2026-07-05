package com.bigbear.goodfish.core;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * ConnectionProperties配置类，用于从配置文件读取默认配置
 */
@Configuration
public class ConnectionPropertiesConfig {

    @Value("${goofish.api-url:https://h5api.m.goofish.com/h5/}")
    private String apiUrl;

    @Value("${goofish.get-token-uri:mtop.taobao.idlemessage.pc.login.token/1.0/}")
    private String tokenUri;

    @Value("${goofish.get-iteminfo-uri:mtop.taobao.idle.pc.detail/1.0/}")
    private String iteminfoUri;

    @Value("${goofish.socket-url:wss://wss-goofish.dingtalk.com/}")
    private String socketUrl;

    @Value("${goofish.https-url:https://www.goofish.com}")
    private String httpsUrl;

    @Value("${goofish.login-check-url:}")
    private String loginCheckUrl;

    @Value("${goofish.upload-url:}")
    private String uploadUrl;

    @Value("${goofish.heartbeat.interval:15}")
    private long heartbeatInterval;

    @Value("${goofish.heartbeat.timeout:30}")
    private int heartbeatTimeout;

    /**
     * 创建新的ConnectionProperties实例并设置默认配置
     */
    public ConnectionProperties createProperties() {
        ConnectionProperties properties = new ConnectionProperties();

        // 设置默认配置值
        properties.setApiUrl(apiUrl);
        properties.setTokenUri(tokenUri);
        properties.setIteminfoUri(iteminfoUri);
        properties.setSocketUrl(URI.create(socketUrl));
        properties.setHttpsUrl(httpsUrl);
        properties.setLoginCheckUrl(loginCheckUrl);
        properties.setUploadUrl(uploadUrl);
        properties.setInterval(heartbeatInterval);
        properties.setTimeout(heartbeatTimeout);

        return properties;
    }


    /**
     * 直接创建并初始化Account实例
     */

}
