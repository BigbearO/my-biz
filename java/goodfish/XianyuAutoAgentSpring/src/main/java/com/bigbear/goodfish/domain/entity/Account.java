package com.bigbear.goodfish.domain.entity;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import org.springframework.util.DigestUtils;

/**
 * 用户账户信息类，存储用户特定的连接和认证信息
 */
@Data
public class Account {
    private Long id;
    // 用户特定属性
    private String cookieStr;
    private Map<String, String> cookies;
    private String userId;
    private String deviceId;
    private String csrfToken;
    private String cookie2;
    private String cna;
    private String token;
    private String deviceIdDataVal;
    private String sign;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

    // APP_KEY常量
    public static final String APP_KEY = "34839810";

    // 构造函数
    public Account() {
    }

    // 带cookie的构造函数
    public Account(String cookieStr) {
        this.cookieStr = cookieStr;
        initialize();
    }

    /**
     * 从cookie中生成token
     */
    public String generateToken() {
        return cookies != null ? Arrays.stream(cookies.getOrDefault("_m_h5_tk", "").split("_")).findFirst().orElse("") : "";
    }

    /**
     * 生成签名
     */
    public String generateSign(String t, String token, String data) {
        String msg = String.format("%s&%s&%s&%s", token, t, APP_KEY, data);
        return DigestUtils.md5DigestAsHex(msg.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成设备ID
     */
    public String generateDeviceId(String userId) {
        // 简化实现，生成一个基本的UUID格式字符串
        return "device-" + userId + "-" + System.currentTimeMillis();
    }

    /**
     * 初始化用户属性，从cookie中提取必要信息
     */
    public void initialize() {
        if (cookieStr != null) {
            cookies = Arrays.stream(cookieStr.split("; "))
                    .map(cookie -> cookie.split("=", 2))  // 最多分割成2部分
                    .filter(parts -> parts.length == 2)    // 只保留有效键值对
                    .collect(Collectors.toMap(
                            parts -> parts[0].trim(),                 // 键
                            parts -> parts[1].trim(),                 // 值
                            (existing, replacement) -> existing // 重复键处理：保留已有值
                    ));
            userId = cookies.getOrDefault("unb", "");
            deviceId = generateDeviceId(userId);
            csrfToken = cookies.getOrDefault("XSRF-TOKEN", "");
            cookie2 = cookies.getOrDefault("cookie2", "");
            cna = cookies.getOrDefault("cna", "");
            token = generateToken();
            deviceIdDataVal = "{\"appKey\": \"444e9908a51d1cb236a27862abc769c9\",\"deviceId\": \"" + deviceId + "\"}";
            sign = generateSign(String.valueOf(System.currentTimeMillis()), generateToken(), deviceIdDataVal);
        }
    }


}
