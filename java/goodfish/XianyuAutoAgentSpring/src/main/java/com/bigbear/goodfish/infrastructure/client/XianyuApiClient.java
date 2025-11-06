package com.bigbear.goodfish.infrastructure.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bigbear.goodfish.infrastructure.util.XianyuUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 闲鱼API客户端
 *
 * @author autoagent
 */
@Slf4j
@Component
public class XianyuApiClient {

    private final String url = "https://h5api.m.goofish.com/h5/mtop.taobao.idlemessage.pc.login.token/1.0/";
    private final HttpClient httpClient;

    // 存储cookies
    private final Map<String, String> cookies = new ConcurrentHashMap<>();

    public XianyuApiClient() {
        this.httpClient = new HttpClient();
    }

    /**
     * 获取token
     */
    public Map<String, Object> getToken(String deviceId) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("appKey", "444e9908a51d1cb236a27862abc769c9");
            params.put("t", String.valueOf(System.currentTimeMillis()));
            params.put("dataType", "json");
            params.put("type", "originaljson");

            Map<String, Object> data = new HashMap<>();
            data.put("deviceId", deviceId);
            params.put("data", JSON.toJSONString(data));

            // 生成签名
            String timestamp = params.get("t");
            String sign = XianyuUtils.generateSign(url, params.get("appKey"), "", timestamp);
            params.put("sign", sign);

            // 发送请求
            String response = httpClient.get(url, params, cookies);

            // 解析响应
            return JSON.parseObject(response, Map.class);

        } catch (Exception e) {
            log.error("获取token失败", e);
            return null;
        }
    }

    /**
     * 检查登录状态
     */
    public boolean hasLogin(int retryCount) {
        if (retryCount >= 2) {
            log.error("登录检查失败，重试次数过多");
            return false;
        }

        try {
            String checkUrl = "https://passport.goofish.com/newlogin/hasLogin.do";
            Map<String, String> params = new HashMap<>();
            params.put("appName", "xianyu");
            params.put("fromSite", "77");

            String response = httpClient.get(checkUrl, params, cookies);
            JSONObject jsonResponse = JSON.parseObject(response);

            return jsonResponse.containsKey("isLogin") && jsonResponse.getBoolean("isLogin");

        } catch (Exception e) {
            log.error("登录检查失败", e);
            return hasLogin(retryCount + 1);
        }
    }

    /**
     * 更新cookies
     */
    public void updateCookies(Map<String, String> newCookies) {
        cookies.putAll(newCookies);
    }

    /**
     * 获取当前cookies
     */
    public Map<String, String> getCookies() {
        return new HashMap<>(cookies);
    }

    /**
     * 清理重复的cookies
     */
    public void clearDuplicateCookies() {
        // 简化实现，实际项目中可能需要更复杂的逻辑
        log.info("清理重复的cookies");
    }

    // 简化的HTTP客户端实现
    private class HttpClient {

        public String get(String url, Map<String, String> params, Map<String, String> cookies) throws IOException {
            // 构建URL
            StringBuilder fullUrl = new StringBuilder(url);
            if (params != null && !params.isEmpty()) {
                fullUrl.append("?");
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    fullUrl.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }
                fullUrl.setLength(fullUrl.length() - 1);
            }

            log.info("发送GET请求: {}", fullUrl);

            // 简化实现，实际项目中应使用HttpClient或RestTemplate等工具
            // 这里仅返回模拟数据
            Map<String, Object> mockResponse = new HashMap<>();
            Map<String, Object> mockData = new HashMap<>();
            mockData.put("accessToken", "mock_token_" + System.currentTimeMillis());
            mockResponse.put("data", mockData);

            return JSON.toJSONString(mockResponse);
        }

        public String post(String url, Map<String, String> headers, String body, Map<String, String> cookies) throws IOException {
            log.info("发送POST请求: {}", url);

            // 简化实现，实际项目中应使用HttpClient或RestTemplate等工具
            // 这里仅返回模拟数据
            Map<String, Object> mockResponse = new HashMap<>();
            mockResponse.put("success", true);

            return JSON.toJSONString(mockResponse);
        }
    }
}
