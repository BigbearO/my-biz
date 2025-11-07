package com.bigbear.goodfish.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;

/**
 * 闲鱼工具类
 *
 * @author autoagent
 */
@Slf4j
public class GoodfishUtils {

    /**
     * 转换cookies字符串为Map
     */
    public static Map<String, String> transCookies(String cookiesStr) {
        Map<String, String> cookies = new HashMap<>();
        if (cookiesStr != null && !cookiesStr.isEmpty()) {
            String[] pairs = cookiesStr.split("; ");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    cookies.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return cookies;
    }

    /**
     * 生成设备ID
     */
    public static String generateDeviceId(String unb) {
        try {
            String raw = unb + "|" + System.currentTimeMillis();
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(raw.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("生成设备ID失败", e);
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * 生成mid
     */
    public static String generateMid() {
        long timestamp = System.currentTimeMillis();
        return timestamp + " " + (int) (Math.random() * 10);
    }

    /**
     * 生成UUID
     */
    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 解密
     */
    public static String decrypt(String encrypted) {
        try {
            /* 1. 清理 + 补 = */
            String cleaned = encrypted.replaceAll("[^A-Za-z0-9+/=]", "");
            while (cleaned.length() % 4 != 0) cleaned += "=";

            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(cleaned);
            } catch (IllegalArgumentException e) {
                return error("Base64 decode failed", e.getMessage(), encrypted);
            }

            /* 2. 先按 MessagePack 解 */
            try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(decoded)) {
                Value val = unpacker.unpackValue();
                JsonNode tree = toJsonNode(val);
                return MAPPER.writeValueAsString(tree);
            } catch (Exception e) {
                /* 3. MP 失败 -> 直接 UTF-8 */
                try {
                    String text = new String(decoded, StandardCharsets.UTF_8);
                    return MAPPER.writeValueAsString(
                        MAPPER.createObjectNode().put("text", text));
                } catch (Exception ex) {
                    /* 4. 最后 fallback -> hex */
                    StringBuilder hex = new StringBuilder();
                    for (byte b : decoded) {
                        hex.append(String.format("%02x", b));
                    }
                    return MAPPER.writeValueAsString(
                        MAPPER.createObjectNode()
                            .put("hex", hex.toString())
                            .put("error", "Decode failed: " + e.getMessage()));
                }
            }
        } catch (Exception e) {
            return error("Decrypt failed", e.getMessage(), encrypted);
        }
    }

    /* ---------------- 内部工具 ---------------- */

    private static JsonNode toJsonNode(Value v) {
        return switch (v.getValueType()) {
            case NIL -> NullNode.getInstance();
            case BOOLEAN -> BooleanNode.valueOf(v.asBooleanValue().getBoolean());
            case INTEGER -> LongNode.valueOf(v.asIntegerValue().asLong());
            case FLOAT -> DoubleNode.valueOf(v.asFloatValue().toDouble());
            case STRING -> TextNode.valueOf(v.asStringValue().asString());
            case BINARY -> {
                byte[] arr = v.asBinaryValue().asByteArray();
                /* 二进制按 python 逻辑：能 UTF-8 就转文本，否则再 base64 */
                try {
                    yield TextNode.valueOf(new String(arr, StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    yield TextNode.valueOf(Base64.getEncoder().encodeToString(arr));
                }
            }
            case ARRAY -> {
                ArrayNode arr = MAPPER.createArrayNode();
                v.asArrayValue().forEach(e -> arr.add(toJsonNode(e)));
                yield arr;
            }
            case MAP -> {
                ObjectNode obj = MAPPER.createObjectNode();
                v.asMapValue().entrySet()
                    .forEach(e -> obj.set(e.getKey().toString(), toJsonNode(e.getValue())));
                yield obj;
            }
            default -> throw new IllegalStateException("unknown msgpack type");
        };
    }

    private static String error(String type, String msg, String raw) {
        try  {
            ObjectNode errorNode = MAPPER.createObjectNode();
            errorNode.put("error", type + ": " + msg);
            errorNode.put("raw_data", raw);
            return MAPPER.writeValueAsString(errorNode);
        } catch (Exception e) {
            return "{\"error\":\"Serialize error object failed\"}";
        }
    }

    /**
     * 生成签名
     */
    public static String generateSign(String url, String appKey, String appSecret, String timestamp) {
        try {
            String raw = url + appKey + timestamp + appSecret;
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(raw.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("生成签名失败", e);
            return "";
        }
    }

    /**
     * 获取当前时间戳（秒）
     */
    public static long currentTimestamp() {
        return LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    }

    /**
     * 获取过期时间
     */
    public static LocalDateTime getExpireTime(long expireTimeInMillis) {
        return LocalDateTime.now().minusNanos(expireTimeInMillis * 1000000);
    }
}
