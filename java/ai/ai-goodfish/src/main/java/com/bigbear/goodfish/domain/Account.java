package com.bigbear.goodfish.domain;

import com.github.curiousoddman.rgxgen.RgxGen;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户账户信息类，存储用户特定的连接和认证信息
 */
@Data
public class Account implements Serializable {

    @Serial
    private static  final long serialVersionUID = 1243L;

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
    // 带cookie的构造函数


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
        final String pattern = "^[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[0-9A-F][89AB][0-9A-F]{2}-[0-9A-F]{12}$";
        RgxGen rgxGen = RgxGen.parse(pattern); // Create generator
        String s = rgxGen.generate();
        return "%s-%s".formatted(s, userId);
    }

    /**
     * 初始化用户属性，从cookie中提取必要信息
     * todo init 方法待验证
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
           String t="1763205100947";//String.valueOf(System.currentTimeMillis());
            token = generateToken();
            deviceIdDataVal =  """
                {"appKey": "444e9908a51d1cb236a27862abc769c9","deviceId": "%s"}
                """.trim().formatted(deviceId);
            sign = generateSign(t, token, deviceIdDataVal);
        }
    }

    public void updateDeviceId(String deviceId) {
        this.deviceId = deviceId;
        String  t=String.valueOf(System.currentTimeMillis());
        token = generateToken();
        deviceIdDataVal =  """
                {"appKey": "444e9908a51d1cb236a27862abc769c9","deviceId": "%s"}
                """.trim().formatted(deviceId);
        sign = generateSign(t, token, deviceIdDataVal);
    }


}
