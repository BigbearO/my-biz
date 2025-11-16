package com.bigbear.goofish.infra;

import static java.lang.invoke.MethodHandles.lookup;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.http.HttpHeaders.PRAGMA;
import static org.springframework.http.HttpHeaders.REFERER;
import static org.springframework.http.HttpHeaders.USER_AGENT;

import com.bigbear.goofish.core.AccountService;
import com.bigbear.goofish.core.ConnectionProperties;
import com.bigbear.goofish.domain.Account;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Lazy
@Slf4j
@Component
public class GoofishClient implements InitializingBean {

    @Autowired
    ConnectionProperties properties;

    @Autowired
    private AccountService accountService;

    @Getter
    private WebClient delegate;

    private final Tika tika = new Tika();


    public static final Map<String, String> COMMON_PARAMS = new HashMap<>() {{
        put("jsv", "2.7.2");
        put("v", "1.0");
        put("type", "originaljson");
        put("accountSite", "xianyu");
        put("dataType", "json");
        put("timeout", "20000");
        put("sessionOption", "AutoLoginOnly");
        put("appKey", "34839810");
        put("api", "mtop.taobao.idlemessage.pc.login.token");
    }};

    //@Retryable(value = {RuntimeException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public boolean hasLogin(String userId) {
        return accountService.isLoggedIn(userId);

        // 保留原有实现作为备用或内联到AccountService中
        /*
        Account account = accountService.getAccount(userId);
        if (account == null) {
            return false;
        }
        String csrfToken = account.getCsrfToken() != null ? account.getCsrfToken() : "";
        String cookie2 = account.getCookie2() != null ? account.getCookie2() : "";
        String cna = account.getCna() != null ? account.getCna() : "";

        ResponseEntity<ObjectNode> response = delegate.post().uri(new DefaultUriBuilderFactory(properties.getLoginCheckUrl()).builder()
                        .queryParams(createMultiValueMap("appName", "xianyu", "fromSite", "77")).build())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)

                .body(BodyInserters.fromFormData(createFormDataMap(
                        new SimpleEntry<>("hid", userId),
                        new SimpleEntry<>("ltl", "true"),
                        new SimpleEntry<>("appName", "xianyu"),
                        new SimpleEntry<>("appEntrance", "web"),
                        new SimpleEntry<>("_csrf_token", csrfToken),
                        new SimpleEntry<>("umidToken", ""),
                        new SimpleEntry<>("hsiz", cookie2),
                        new SimpleEntry<>("bizParams", "taobaoBizLoginFrom=web"),
                        new SimpleEntry<>("mainPage", "false"),
                        new SimpleEntry<>("isMobile", "false"),
                        new SimpleEntry<>("lang", "zh_CN"),
                        new SimpleEntry<>("returnUrl", ""),
                        new SimpleEntry<>("fromSite", "77"),
                        new SimpleEntry<>("isIframe", "true"),
                        new SimpleEntry<>("documentReferer", properties.getHttpsUrl()),
                        new SimpleEntry<>("defaultView", "hasLogin"),
                        new SimpleEntry<>("umidTag", "SERVER"),
                        new SimpleEntry<>("deviceId", cna)
                ))).retrieve()
                .toEntity(ObjectNode.class)
                .block(Duration.ofSeconds(5));

        return response.getStatusCode().is2xxSuccessful() &&
                response.getBody() != null &&
                "success".equals(response.getBody()
                        .path("content")
                        .path("data")
                        .path("loginResult")
                        .asText());
        */
    }

    /*spm_pre=a21ybx.personal.sidebar.1.494f6ac2w6LFhH&log_id=494f6ac2w6LFhH*/
    //@Retryable(value = {RuntimeException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Mono<String> getToken(Account account) {

        String userId =account.getUserId();
        String deviceIdDataVal = account.getDeviceIdDataVal();
        log.info("start fetching access token for webSocket connection for user: {}", userId);

       final   Map<String, String> cookies = Arrays.stream(account.getCookieStr().split("; "))
            .map(cookie -> cookie.split("=", 2))
            .filter(parts -> parts.length == 2)
            .collect(Collectors.toMap(
                parts -> parts[0],
                parts -> parts[1],
                (existing, replacement) -> existing
            ));

        return delegate.post()
                .uri(builder -> {
                    builder.path(properties.getTokenUri());
                    COMMON_PARAMS.forEach(builder::queryParam);

//                    String time = account.getT();
//                    String token = account.getToken();
                    //     String sign = account.getSign();
                    // 通过Account对象获取token和生成签名
                    String token = account.generateToken();
                    String time =
//                        "1763210559524";
                    String.valueOf(System.currentTimeMillis());
                    String sign = account.generateSign(time, token, deviceIdDataVal);
                    URI build = builder
                        .queryParam("sign", sign)
                        .queryParam("spm_cnt", "a21ybx.im.0.0")
                        .queryParam("t", time)
                        .build();
                    log.info("finalUri={}",build.toString());
                    return build;
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .cookies(cookiesConsumer -> cookies.forEach(cookiesConsumer::add))
//                .bodyValue(createMultiValueMap("data", deviceIdDataVal))
                .bodyValue(MultiValueMap.fromSingleValue(Map.of("data", deviceIdDataVal)))
                .retrieve()
                .bodyToMono(ObjectNode.class)
                    .flatMap(response -> {
                    String ret = response.path("ret").toString();
                    if (ret.contains("SUCCESS::调用成功")) {
                        String accessToken = response.path("data").path("accessToken").asText();
                        log.info("用户 {} 生成令牌: {}", userId, accessToken);
                        return Mono.just(accessToken);
                    }
                    return Mono.error(new RuntimeException("获取Token失败: " + response));
                });
    }

    private final static String pvid = new Random().ints(14, 0, 60)
            .mapToObj(i -> String.valueOf("0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz".charAt(i)))
            .collect(Collectors.joining());

    public Mono<JsonNode> getItemInfo(String userId, String itemId) {
        Account account = accountService.getAccount(userId);
        if (account == null) {
            return Mono.error(new RuntimeException("未找到用户 " + userId + " 的账户信息"));
        }

        String time = String.valueOf(System.currentTimeMillis());
        String itemIdDataVal = String.format("{\"itemId\": \"%s\"}", itemId);
        log.info("send https requests to retrieve item info for: {} by user: {}", itemId, userId);
        return delegate.post()
                .uri(builder -> {
                    builder.path(properties.getIteminfoUri());
                    COMMON_PARAMS.forEach(builder::queryParam);

                    // 通过Account对象获取token和生成签名
                    String token = account.generateToken();
                    String sign = account.generateSign(time, token, itemIdDataVal);

                    return builder
                            .queryParam("sign", sign)
                            .queryParam("spm_cnt", "a21ybx.item.0.0")
                            .queryParam("spm_pre", String.format("a21ybx.personal.feeds.%d.%s",
                                    new Random().nextInt(50) + 1, pvid
                            ))
                            .queryParam("t", time)
                            .build();
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(createMultiValueMap("data", itemIdDataVal))
                .retrieve()
                .bodyToMono(ObjectNode.class)
                .flatMap(response -> {
                    String ret = response.path("ret").toString();
                    if (ret.contains("SUCCESS::调用成功")) {
                        log.info("get item info successful for user: {}", userId);
                        return Mono.just(response.path("data"));
                    }
                    return Mono.error(new RuntimeException("API调用失败: " + response));
                });
    }

    /**
     * 通过文件系统路径上传
     */
    public Mono<ResponseEntity<JsonNode>> upload(Path filePath) {
        return Mono.using(
                () -> Files.newInputStream(filePath),
                is -> upload(is, filePath.getFileName().toString()),
                this::closeQuietly
        );
    }

    /**
     * 通过Spring Resource上传
     */
    public Mono<ResponseEntity<JsonNode>> upload(Resource resource) {
        return Mono.using(
                resource::getInputStream,
                is -> upload(is, resource.getFilename()),
                this::closeQuietly
        );
    }

    /**
     * 通过输入流上传（需提供文件名）
     */
    @SneakyThrows
    public Mono<ResponseEntity<JsonNode>> upload(InputStream inputStream, String filename) {
        // 复制 InputStream 以便重复使用
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 替换transferTo方法以兼容Java 8
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        byte[] bytes = baos.toByteArray();

        return Mono.fromCallable(() -> {
                    MediaType contentType = detectContentType(inputStream, filename);

                    return new FilePart() {
                        @Override
                        public String filename() {
                            return "uploadImg"; // 固定文件名
                        }

                        @Override
                        public Flux<DataBuffer> content() {
                            // 每次调用都从字节数组新建流
                            return DataBufferUtils.readInputStream(
                                    () -> new ByteArrayInputStream(bytes),
                                    new DefaultDataBufferFactory(),
                                    4096
                            );
                        }

                        @Override
                        public HttpHeaders headers() {
                            HttpHeaders headers = new HttpHeaders();
                            headers.setContentDispositionFormData("file", "uploadImg");
                            headers.setContentType(detectContentType(inputStream, filename));
                            return headers;
                        }

                        @Override
                        public String name() {
                            return "file";
                        }

                        @Override
                        public Mono<Void> transferTo(Path dest) {
                            return DataBufferUtils.write(content(), dest);
                        }
                    };
                })
                .flatMap(this::upload);
    }

    public Mono<ResponseEntity<JsonNode>> upload(FilePart filePart) {
        // 1. 生成唯一的boundary（模拟WebKit格式）
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // 2. 构建URI（保持原样）
        URI uploadUrl = new DefaultUriBuilderFactory(properties.getUploadUrl()).builder()
                .queryParams(createMultiValueMap("floderId", "0", "appkey", "xy_chat", "_input_charset", "utf-8")).build();

        // 3. 构建Multipart请求体
        return filePart.content()
                .collectList()
                .flatMap(dataBuffers -> {
                    // 合并DataBuffer
                    DataBuffer buffer = new DefaultDataBufferFactory().join(dataBuffers);
                    byte[] fileBytes = new byte[buffer.readableByteCount()];
                    buffer.read(fileBytes);
                    DataBufferUtils.release(buffer);

                    // 自动检测类型
                    MediaType fileContentType = detectContentType(fileBytes, filePart.filename());

                    // 构建multipart body（关键修改点）
                    MultipartBodyBuilder builder = new MultipartBodyBuilder();
                    builder.part("file", new ByteArrayResource(fileBytes) {
                                @Override
                                public String getFilename() {
                                    return "uploadImg"; // 固定文件名
                                }
                            })
                            .contentType(fileContentType);

                    // 4. 发送请求（精确匹配Content-Type）
                    return delegate.post()
                            .uri(uploadUrl)
                            .contentType(MediaType.parseMediaType(
                                    "multipart/form-data; boundary=" + boundary // 关键修改
                            ))
                            .headers(headers -> {
                                // 设置固定值头
                                headers.set("Accept", "*/*");
                                headers.set("Accept-Encoding", "gzip, deflate, br, zstd");
                                headers.set("Access-Control-Allow-Origin", "*");
                                headers.set("Priority", "u=1, i");
                                headers.set("Sec-Ch-Ua-Mobile", "?0");
                                headers.set("Sec-Fetch-Dest", "empty");
                                headers.set("Sec-Fetch-Mode", "cors");
                                headers.set("Sec-Fetch-Site", "same-site");
                                headers.set("X-Requested-With", "XMLHttpRequest");

                                // 设置从配置获取的头
                                headers.set("Origin", properties.getHttpsUrl());
                                headers.set("Referer", properties.getHttpsUrl());
                                headers.set("User-Agent", properties.getUserAgent());
                                headers.set("Sec-Ch-Ua", properties.getUserAgent());
                                headers.set("Sec-Ch-Ua-Platform", "Windows");

                                // 设置复杂值头
                                headers.set("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7,ja-JP;q=0.6,ja;q=0.5,ko-KR;q=0.4,ko;q=0.3");
                            })
                            .body(BodyInserters.fromMultipartData(builder.build()))
                            .retrieve()
                            .toEntity(JsonNode.class);
                });
    }

    private MediaType detectContentType(byte[] content, String filename) {
        try {
            String mimeType = tika.detect(content, filename);
            return MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private MediaType detectContentType(InputStream inputStream, String filename) {
        try {
            String mimeType = tika.detect(inputStream, filename);
            return MediaType.parseMediaType(mimeType);
        } catch (IOException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ignored) {
        }
    }

    // 辅助方法：创建MultiValueMap（兼容Java 8）
    private MultiValueMap<String, String> createMultiValueMap(String... keyValuePairs) {
        LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i + 1 < keyValuePairs.length) {
                map.add(keyValuePairs[i], keyValuePairs[i + 1]);
            }
        }
        return map;
    }

    // 辅助方法：从SimpleEntry数组创建MultiValueMap
    private MultiValueMap<String, String> createFormDataMap(SimpleEntry<String, String>... entries) {
        LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (SimpleEntry<String, String> entry : entries) {
            map.add(entry.getKey(), entry.getValue());
        }
        return map;
    }

    @Override
    public void afterPropertiesSet() {

        this.delegate = WebClient.builder()
                .baseUrl(properties.getApiUrl())
                .defaultHeaders(headers -> {
                    headers.add(ACCEPT, "application/json");
                    headers.add(ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
                    headers.add(CACHE_CONTROL, "no-cache");
                    headers.add(ORIGIN, properties.getHttpsUrl());
                    headers.add(PRAGMA, "no-cache");
                    headers.add("priority", "u=1, i");
                    headers.add(REFERER, properties.getHttpsUrl());
                    headers.add("sec-ch-ua", "'Not(A:Brand';v='99', 'Google Chrome';v='133', 'Chromium';v='133'");
                    headers.add("sec-ch-ua-mobile", "?0");
                    headers.add("sec-ch-ua-platform", "Windows");
                    headers.add("sec-fetch-dest", "empty");
                    headers.add("sec-fetch-mode", "cors");
                    headers.add("sec-fetch-site", "same-site");
                    headers.add(USER_AGENT, properties.getUserAgent());
                })

            //todo  注释了cookie 调用时 改成
//        webClient.get()
//                .uri("/api")
//                .cookie("token", token)      // ← 动态参数
//                .cookie("SESSION", sessionId)
//                .retrieve()
//                .bodyToMono(String.class);

              // 2. 注册过滤器：每次请求前动态设置 Cookie
//        .filter((request, next) -> {
//            // 2-1 这里可以从任何地方实时读取
//            String latestToken = TokenHolder.getLatest();   // 你自己的逻辑
//            String sessionId = SessionStore.current();      // 你自己的逻辑
//
//            // 2-2 把 Cookie 塞进新请求
//            ClientRequest filtered = ClientRequest.from(request)
//                .cookies(cookies -> {
//                    cookies.add("token", latestToken);
//                    cookies.add("SESSION", sessionId);
//                })
//                .build();
//            return next.exchange(filtered);
//        })
                .build();
    }
}


/* // 1. 调用处：把 token 放进 Context
Mono<String> resp = webClient
        .get()
        .uri("/api")
        .contextWrite(Context.of("token", token, "sessionId", sessionId))
        .retrieve()
        .bodyToMono(String.class);



// 2. 过滤器：从 Context 读
WebClient client = WebClient.builder()
        .filter((req, next) ->
                Mono.deferContextual(ctx -> {
                    String tk   = ctx.getOrDefault("token", "");
                    String sess = ctx.getOrDefault("sessionId", "");
                    ClientRequest filtered = ClientRequest.from(req)
                            .cookies(c -> {
                                if (!tk.isEmpty())   c.add("token", tk);
                                if (!sess.isEmpty()) c.add("SESSION", sess);
                            })
                            .build();
                    return next.exchange(filtered);
                }))
        .build();

        todo 2
        // 1. 调用处
Mono<String> resp = webClient
        .get()
        .uri("/api")
        .attribute("token", token)        // ← 请求级别私有数据
        .attribute("sessionId", sessionId)
        .retrieve()
        .bodyToMono(String.class);

// 2. 过滤器
WebClient client = WebClient.builder()
        .filter((req, next) -> {
            String tk   = (String) req.attribute("token").orElse("");
            String sess = (String) req.attribute("sessionId").orElse("");
            ClientRequest filtered = ClientRequest.from(req)
                    .cookies(c -> {
                        if (!tk.isEmpty())   c.add("token", tk);
                        if (!sess.isEmpty()) c.add("SESSION", sess);
                    })
                    .build();
            return next.exchange(filtered);
        })
        .build();


        */
