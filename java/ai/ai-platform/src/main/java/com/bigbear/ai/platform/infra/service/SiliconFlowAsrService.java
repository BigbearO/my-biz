package com.bigbear.ai.platform.infra.service;

import static com.bigbear.ai.platform.infra.vad.App.AUDIO_FILE_PATH;

import cn.hutool.core.io.FileUtil;
import com.bigbear.ai.platform.domain.vo.VoiceAsrResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


/**
 * SiliconFlow ASR语音识别服务
 * 使用JDK 21 HTTP Client调用SiliconFlow API
 */
@Slf4j
@Service
public class SiliconFlowAsrService {



    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    @Value("${voice.asr.siliconflow.api-key}")
    private   String apiKey;
    @Value("${voice.asr.siliconflow.api-url:https://api.siliconflow.cn/v1/audio/transcriptions}")
    private   String apiUrl;
    @Value("${voice.asr.siliconflow.model:FunAudioLLM/SenseVoiceSmall}")
    private String model;

    public SiliconFlowAsrService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();

    }


    /**
     * 调用ASR接口识别语音
     *
     * @param audioBytes WAV音频数据(直接字节数组，不是base64)
     * @return 识别结果
     */
    public VoiceAsrResponse recognize(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new RuntimeException("音频数据为空");
        }

        log.debug("开始ASR识别，音频数据大小: {} bytes", audioBytes.length);

        try {
            audioBytes= FileUtil.readBytes(AUDIO_FILE_PATH);
            String boundary = "Boundary-" + System.currentTimeMillis();

            // 构建multipart/form-data请求体
            byte[] requestBody = buildMultipartBody(audioBytes,boundary);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary="+boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode jsonNode = objectMapper.readTree(response.body());
                String text = jsonNode.has("text") ? jsonNode.get("text").asText() : "";
                log.info("ASR识别成功: {}", text);
                return VoiceAsrResponse.builder()
                        .text(text)
                        .hasSpeech(true)
                        .build();
            } else {
                log.error("ASR接口调用失败，状态码: {}，响应: {}", response.statusCode(), response.body());
                throw new RuntimeException("ASR服务调用失败: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("ASR接口调用异常", e);
            throw new RuntimeException("ASR服务调用异常", e);
        }
    }

    /**
     * 构建multipart/form-data请求体
     * 格式: curl -F "file=@audio.wav" -F "model=FunAudioLLM/SenseVoiceSmall"
     */
    private byte[] buildMultipartBody(byte[] audioBytes, String boundary) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] lineBreak = "\r\n".getBytes();
        // 文件部分
        baos.write(("--"+boundary+"\r\n").getBytes());
        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n").getBytes());
        baos.write(("Content-Type: audio/wav\r\n\r\n").getBytes());
        baos.write(audioBytes);
        baos.write(("\r\n").getBytes());

        // 模型参数部分
        baos.write(("--"+boundary+"\r\n").getBytes());
        baos.write(("Content-Disposition: form-data; name=\"model\"\r\n\r\n").getBytes());
        baos.write(model.getBytes());
        baos.write(("\r\n").getBytes());

        // 结束标记
        baos.write(("--"+boundary+"\r\n").getBytes());

        return baos.toByteArray();
    }
}
