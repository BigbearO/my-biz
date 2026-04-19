package com.bigbear.ai.platform.trigger.api;

import com.bigbear.ai.platform.domain.vo.VoiceListResponse;
import com.bigbear.ai.platform.infra.service.EdgeTtsService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * TTS 语音合成 REST 接口
 * <p>
 * 对应 docs/tts.md 2.7 节。
 * 独立于 AudioChatController，映射 /api/tts/v2。
 */
@RestController
@RequestMapping("/api")
@Validated
public class TtsController {

    private final EdgeTtsService edgeTtsService;

    public TtsController(EdgeTtsService edgeTtsService) {
        this.edgeTtsService = edgeTtsService;
    }

    /**
     * 文本转语音，流式返回 MP3 音频。
     * 对应 Python: GET /api/tts
     */
    @GetMapping(path = "/tts", produces = "audio/mpeg")
    public ResponseEntity<Flux<byte[]>> textToSpeech(
            @RequestParam @NotBlank(message = "text 不能为空") String text,
            @RequestParam(required = false) String voice,
            @RequestParam(required = false) String rate,
            @RequestParam(required = false) String pitch
    ) {
        String trimmedText = text.strip();

        Flux<byte[]> audioFlux = edgeTtsService.synthesize(trimmedText, voice, rate, pitch);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .header("Content-Disposition", "inline")
            .body(audioFlux);
    }

//    /**
//     * 获取可用语音列表
//     */
//    @GetMapping("/voices")
//    public Mono<VoiceListResponse> listVoices() {
//        return Mono.fromCallable(() -> {
//            var voices = edgeTtsService.listVoices();
//            return VoiceListResponse.builder()
//                .current(edgeTtsService.getCurrentVoice())
//                .voices(voices)
//                .build();
//        });
//    }
}
