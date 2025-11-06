package com.bigbear.goodfish2.domain.dto;

import java.util.Map;
import lombok.Data;

@Data
//@EqualsAndHashCode(callSuper = true)
public class AccountDTO {

    private Long id;

    private String userId;

    private Map<String, String> cookies;

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

}
