package com.bigbear.ai.ai3d.infra.service;

import com.bigbear.ai.ai3d.domain.vo.VoiceAsrResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
public class GiteeAsrService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public GiteeAsrService(
            @Value("${voice.asr.gitee.api-key}") String apiKey,
            @Value("${voice.asr.gitee.api-url:https://ai.gitee.com/v1/audio/transcriptions}") String apiUrl,
            @Value("${voice.asr.gitee.model:GLM-ASR}") String model) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
    }

    public VoiceAsrResponse recognize(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new RuntimeException("音频数据为空");
        }

        log.debug("开始Gitee ASR识别，音频数据大小: {} bytes", audioBytes.length);

        try {
            String boundary = "Boundary-" + System.currentTimeMillis();
            byte[] requestBody = buildMultipartBody(audioBytes, boundary);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode jsonNode = objectMapper.readTree(response.body());
                String text = jsonNode.has("text") ? jsonNode.get("text").asText() : "";
                log.info("Gitee ASR识别成功: {}", text);
                return VoiceAsrResponse.builder()
                        .text(text)
                        .hasSpeech(true)
                        .build();
            } else {
                log.error("Gitee ASR接口调用失败，状态码: {}，响应: {}", response.statusCode(), response.body());
                throw new RuntimeException("Gitee ASR服务调用失败: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Gitee ASR接口调用异常", e);
            throw new RuntimeException("Gitee ASR服务调用异常", e);
        }
    }

    private byte[] buildMultipartBody(byte[] audioBytes, String boundary) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        baos.write(("--" + boundary + "\r\n").getBytes());
        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio.mp3\"\r\n").getBytes());
        baos.write(("Content-Type: audio/mpeg\r\n\r\n").getBytes());
        baos.write(audioBytes);
        baos.write(("\r\n").getBytes());

        baos.write(("--" + boundary + "\r\n").getBytes());
        baos.write(("Content-Disposition: form-data; name=\"model\"\r\n\r\n").getBytes());
        baos.write(model.getBytes());
        baos.write(("\r\n").getBytes());

        baos.write(("--" + boundary + "--\r\n").getBytes());

        return baos.toByteArray();
    }
}
