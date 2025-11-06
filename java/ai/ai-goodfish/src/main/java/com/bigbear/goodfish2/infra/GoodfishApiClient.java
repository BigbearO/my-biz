package com.bigbear.goodfish2.infra;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bigbear.goodfish2.infra.util.GoodfishUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 闲鱼API客户端
 *
 * @author autoagent
 */
@Slf4j
@Component
public class GoodfishApiClient {

    private final String url = "https://h5api.m.goofish.com/h5/mtop.taobao.idlemessage.pc.login.token/1.0/";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // 存储cookies
    private final Map<String, String> cookies = new ConcurrentHashMap<>();

    public GoodfishApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
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
            String sign = GoodfishUtils.generateSign(url, params.get("appKey"), "", timestamp);
            params.put("sign", sign);

            // 发送请求
            String response = doGet(url, params, cookies);

            // 解析响应
            return objectMapper.readValue(response, Map.class);

        } catch (Exception e) {
            log.error("获取token失败", e);
            return null;
        }
    }

    public Mono<String> getTokens(String deviceId) {
        try {
//            Map<String, String> params = new HashMap<>();
//            params.put("appKey", "444e9908a51d1cb236a27862abc769c9");
//            params.put("t", String.valueOf(System.currentTimeMillis()));
//            params.put("dataType", "json");
//            params.put("type", "originaljson");
//
//            Map<String, Object> data = new HashMap<>();
//            data.put("deviceId", deviceId);
//            params.put("data", JSON.toJSONString(data));
//
//            // 生成签名
//            String timestamp = params.get("t");
//            String sign = GoodfishUtils.generateSign(url, params.get("appKey"), "", timestamp);
//            params.put("sign", sign);
//
//            // 发送请求
//            String response = doGet(url, params, cookies);

            // 解析响应
//            return objectMapper.readValue(response, Map.class);

            return null;
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

            String response = doGet(checkUrl, params, cookies);
            Map<String, Object> jsonResponse = objectMapper.readValue(response, Map.class);

            return jsonResponse.containsKey("isLogin") && Boolean.TRUE.equals(jsonResponse.get("isLogin"));

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
        //todo-x
        // 简化实现，实际项目中可能需要更复杂的逻辑
        log.info("清理重复的cookies");
    }

    /**
     * 发送GET请求
     */
    private String doGet(String url, Map<String, String> params, Map<String, String> cookies) throws IOException, InterruptedException {
        // 构建URL
        StringBuilder fullUrl = new StringBuilder(url);
        if (params != null && !params.isEmpty()) {
            fullUrl.append("?");
            String queryString = params.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&"));
            fullUrl.append(queryString);
        }

        log.info("发送GET请求: {}", fullUrl);

        // 构建请求
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl.toString()))
                .timeout(Duration.ofSeconds(30))
                .GET();

        // 添加cookies
        if (cookies != null && !cookies.isEmpty()) {
            String cookieString = cookies.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; "));
            requestBuilder.header("Cookie", cookieString);
        }

        // 发送请求并获取响应
        HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        return response.body();
    }

    /**
     * 发送POST请求
     */
    private String doPost(String url, Map<String, String> headers, String body, Map<String, String> cookies) throws IOException, InterruptedException {
        log.info("发送POST请求: {}", url);

        // 构建请求
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");

        // 添加自定义headers
        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }

        // 添加cookies
        if (cookies != null && !cookies.isEmpty()) {
            String cookieString = cookies.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; "));
            requestBuilder.header("Cookie", cookieString);
        }

        // 设置请求体
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        // 发送请求并获取响应
        HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        return response.body();
    }
}
