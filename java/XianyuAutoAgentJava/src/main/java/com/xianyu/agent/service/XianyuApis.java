package com.xianyu.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.xianyu.agent.util.XianyuUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class XianyuApis {

    @Value("${xianyu.cookie}")
    private String cookie;

    @Resource
    private RestTemplate restTemplate;
    @Resource
    private ObjectMapper objectMapper;

    public boolean checkLoginStatus() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", cookie);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.m.taobao.com/json/h5/mtop.taobao.login.checktoken/1.0/",
                    HttpMethod.GET,
                    entity,
                    String.class);
            
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> getToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", cookie);
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            
            String requestBody = "api=mtop.taobao.login.getToken&v=1.0";
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://acs.m.taobao.com/gw/mtop.taobao.login.getToken/1.0/",
                    HttpMethod.POST,
                    entity,
                    String.class);
            
            return objectMapper.readValue(response.getBody(), Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public Map<String, Object> getItemInfo(String itemId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", cookie);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = String.format("https://market.m.taobao.com/app/idleFish-F2e/IdleFishProductDetail/pc/detail?id=%s", itemId);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            // 解析商品信息，实际逻辑需要根据API响应格式进行调整
            Map<String, Object> result = new HashMap<>();
            result.put("itemId", itemId);
            result.put("title", "商品标题");
            result.put("price", "0");
            
            return result;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public String refreshToken() {
        // 实现token刷新逻辑
        Map<String, Object> tokenInfo = getToken();
        if (tokenInfo.containsKey("token")) {
            return tokenInfo.get("token").toString();
        }
        return null;
    }
}
