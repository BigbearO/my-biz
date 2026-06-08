package com.bigbear.ai.ai3d.trigger.api;

import com.bigbear.ai.ai3d.domain.vo.VoiceAsrResponse;
import com.bigbear.ai.ai3d.infra.service.GiteeAsrService;
import com.bigbear.ai.ai3d.infra.service.SiliconFlowAsrService;
import com.bigbear.ai.ai3d.infra.vad.ConcurrentVadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceAsrController {

    private final ConcurrentVadService vadService;
    private final SiliconFlowAsrService siliconFlowAsrService;
    private final GiteeAsrService giteeAsrService;

    @PostMapping("/asr")
    public ResponseEntity<VoiceAsrResponse> recognize(@RequestParam("file") MultipartFile file) {
        log.info("收到ASR请求，文件名: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());

        byte[] audioBytes;
        try {
            audioBytes = file.getBytes();
        } catch (Exception e) {
            log.error("文件读取失败", e);
            return ResponseEntity.badRequest().build();
        }

        if (audioBytes.length == 0) {
            return ResponseEntity.badRequest().build();
        }

        boolean hasSpeech = vadService.detectSpeech(audioBytes);
        log.info("VAD检测结果: {}", hasSpeech);

        if (!hasSpeech) {
            return ResponseEntity.ok(VoiceAsrResponse.builder()
                    .text("")
                    .hasSpeech(false)
                    .build());
        }

        try {
            VoiceAsrResponse response = giteeAsrService.recognize(audioBytes);
            response.setHasSpeech(true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("ASR识别异常", e);
            return ResponseEntity.status(502).build();
        }
    }
}
