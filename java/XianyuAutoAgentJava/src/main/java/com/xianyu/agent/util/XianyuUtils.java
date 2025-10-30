package com.xianyu.agent.util;

import org.apache.commons.codec.digest.DigestUtils;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XianyuUtils {
    
    public static String parseCookie(String cookieStr, String name) {
        Pattern pattern = Pattern.compile(name + "=([^;]+)");
        Matcher matcher = pattern.matcher(cookieStr);
        if (matcher.find()) {
            try {
                return URLDecoder.decode(matcher.group(1), "UTF-8");
            } catch (Exception e) {
                return matcher.group(1);
            }
        }
        return null;
    }

    public static String generateMid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateDeviceId() {
        String[] prefixes = {"A", "B", "C", "D"};
        String prefix = prefixes[new Random().nextInt(prefixes.length)];
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    public static String generateSign(String data, String appKey, String appSecret) {
        String str = data + appSecret;
        return DigestUtils.md5Hex(str);
    }

    public static String base64Encode(String data) {
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64Decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    public static String decryptData(String encryptedData, String key) {
        // 简化实现，实际需要根据Python代码中的解密逻辑进行完善
        return encryptedData;
    }

    public static boolean isBargainMessage(String message) {
        String[] keywords = {"便宜", "优惠", "降价", "少点", "打折", "贵", "便宜点"};
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOrderMessage(String message) {
        String[] keywords = {"下单", "拍了", "购买", "付款", "已拍"};
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
