package com.bigbear.ai.platform.trigger.api;

import com.bigbear.ai.platform.domain.vo.VoiceAsrResponse;
import com.bigbear.ai.platform.infra.service.SiliconFlowAsrService;
import com.bigbear.ai.platform.infra.vad.ConcurrentVadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 语音识别接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/voice")
@Tag(name = "语音识别", description = "语音转文字相关接口")
@Validated
public class VoiceAsrController {

    private final ConcurrentVadService vadService;

    private final SiliconFlowAsrService siliconFlowAsrService;

    /**
     * 语音识别接口 接收WAV音频文件，返回ASR识别结果
     *
     * @param file WAV音频文件
     * @return 识别结果
     */
    @Operation(summary = "语音识别", description = "接收WAV音频文件，返回ASR识别结果")
    @PostMapping("/asr")
    public R<VoiceAsrResponse> recognize(@RequestParam("file") MultipartFile file) {
        log.info("收到ASR请求，文件名: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());

        byte[] audioBytes;
        try {
            audioBytes = file.getBytes();
        } catch (Exception e) {
            log.error("文件读取失败", e);
            return R.fail(400, "文件读取失败");
        }

        if (audioBytes.length == 0) {
            return R.fail(400, "音频数据不能为空");
        }

        boolean hasSpeech = vadService.detectSpeech(audioBytes);
        log.info("VAD检测结果: {}", hasSpeech);

        if (!hasSpeech) {
            return R.ok(VoiceAsrResponse.builder()
                .text("")
                .hasSpeech(false)
                .build());
        }

        try {
            VoiceAsrResponse response = siliconFlowAsrService.recognize(audioBytes);
            response.setHasSpeech(true);
            return R.ok(response);
        } catch (Exception e) {
            log.error("ASR识别异常", e);
            return R.fail(502, "ASR服务调用失败: " + e.getMessage());
        }
    }
}
