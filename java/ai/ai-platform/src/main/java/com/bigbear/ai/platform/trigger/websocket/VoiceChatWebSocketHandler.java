package com.bigbear.ai.platform.trigger.websocket;

import com.bigbear.ai.platform.domain.vo.AgentContext;
import com.bigbear.ai.platform.domain.vo.ResponseChunk;
import com.bigbear.ai.platform.domain.vo.VoiceChatMessage;
import com.bigbear.ai.platform.infra.processor.AudioDataHandler;
import com.bigbear.ai.platform.infra.processor.VadProcessor;
import com.bigbear.ai.platform.infra.service.AsrService;
import com.bigbear.ai.platform.infra.service.LlmService;
import com.bigbear.ai.platform.infra.service.TtsService;
import com.bigbear.ai.platform.infra.manager.ResponseManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音对话WebSocket处理器
 */
@Slf4j
@Component
public class VoiceChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AudioDataHandler audioDataHandler;
    private final VadProcessor vadProcessor;
    private final AsrService asrService;
    private final LlmService llmService;
    private final TtsService ttsService;
    private final ResponseManager responseManager;

    /**
     * sessionId -> AgentContext
     */
    private final Map<String, AgentContext> sessions = new ConcurrentHashMap<>();
    
    /**
     * sessionId -> 累积的音频数据
     */
    private final Map<String, byte[]> audioBuffers = new ConcurrentHashMap<>();

    public VoiceChatWebSocketHandler(ObjectMapper objectMapper,
                                     AudioDataHandler audioDataHandler,
                                     VadProcessor vadProcessor,
                                     AsrService asrService,
                                     LlmService llmService,
                                     TtsService ttsService,
                                     ResponseManager responseManager) {
        this.objectMapper = objectMapper;
        this.audioDataHandler = audioDataHandler;
        this.vadProcessor = vadProcessor;
        this.asrService = asrService;
        this.llmService = llmService;
        this.ttsService = ttsService;
        this.responseManager = responseManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从session获取AgentContext
        Map<String, Object> attributes = session.getAttributes();
        AgentContext agentContext = VoiceChatHandshakeInterceptor.getAgentContext(attributes);
        
        if (agentContext == null) {
            log.warn("WebSocket连接失败: 无法获取AgentContext");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        
        String sessionId = session.getId();
        agentContext.setSessionId(sessionId);
        sessions.put(sessionId, agentContext);
        audioBuffers.put(sessionId, new byte[0]);
        
        log.info("WebSocket连接建立: sessionId={}, agentId={}", sessionId, agentContext.getAgentId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        AgentContext agentContext = sessions.get(sessionId);
        
        if (agentContext == null) {
            log.warn("会话不存在: {}", sessionId);
            return;
        }
        
        String payload = message.getPayload();
        VoiceChatMessage chatMessage = objectMapper.readValue(payload, VoiceChatMessage.class);
        
        String type = chatMessage.getType();
        log.debug("收到消息: type={}, sessionId={}", type, sessionId);
        
        switch (type) {
            case "hello":
                handleHello(session, chatMessage);
                break;
            case "listen":
                handleListen(session, chatMessage);
                break;
            case "abort":
                handleAbort(session, chatMessage);
                break;
            default:
                log.warn("未知消息类型: {}", type);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, org.springframework.web.socket.BinaryMessage message) {
        String sessionId = session.getId();
        AgentContext agentContext = sessions.get(sessionId);
        
        if (agentContext == null) {
            log.warn("会话不存在: {}", sessionId);
            return;
        }
        
        byte[] payload = message.getPayload().array();
        
        // 空字节表示录音结束
        if (payload.length == 0) {
            log.info("收到录音结束信号: sessionId={}", sessionId);
            processAudioEnd(session, agentContext);
            return;
        }
        
        // VAD检测
        vadProcessor.process(payload, agentContext.getAgentId(), new VadProcessor.VadCallback() {
            @Override
            public void onSpeechStart() {
                log.info("检测到语音开始: sessionId={}", sessionId);
            }
            
            @Override
            public void onSpeechEnd(byte[] completeAudio) {
                log.info("检测到语音结束: sessionId={}, audioLength={}", sessionId, completeAudio.length);
                // 进行ASR识别
                asrService.recognize(completeAudio, new AsrService.AsrCallback() {
                    @Override
                    public void onResult(String text) {
                        log.info("ASR识别结果: sessionId={}, text={}", sessionId, text);
                        // 发送STT结果给前端
                        sendSttMessage(session, text);
                        
                        // 调用LLM并处理响应
                        llmService.chat(agentContext, text, new LlmService.LlmCallback() {
                            @Override
                            public void onChunk(ResponseChunk chunk) {
                                // 发送文字和语音给前端
                                sendResponseChunk(session, chunk);
                            }
                            
                            @Override
                            public void onComplete() {
                                log.info("LLM响应完成: sessionId={}", sessionId);
                            }
                            
                            @Override
                            public void onError(Throwable t) {
                                log.error("LLM调用失败: sessionId={}", sessionId, t);
                                sendErrorMessage(session, t.getMessage());
                            }
                        });
                    }
                    
                    @Override
                    public void onError(Throwable t) {
                        log.error("ASR识别失败: sessionId={}", sessionId, t);
                        sendErrorMessage(session, "ASR识别失败: " + t.getMessage());
                    }
                });
            }
        });
        
        // 累积音频数据
        byte[] buffer = audioBuffers.get(sessionId);
        byte[] newBuffer = new byte[buffer.length + payload.length];
        System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
        System.arraycopy(payload, 0, newBuffer, buffer.length, payload.length);
        audioBuffers.put(sessionId, newBuffer);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        audioBuffers.remove(sessionId);
        log.info("WebSocket连接关闭: sessionId={}, status={}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket传输错误: sessionId={}", session.getId(), exception);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            log.error("关闭session失败", e);
        }
    }

    // ===== 消息处理方法 =====

    private void handleHello(WebSocketSession session, VoiceChatMessage message) throws IOException {
        VoiceChatMessage response = new VoiceChatMessage();
        response.setType("hello");
        response.setSessionId(session.getId());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleListen(WebSocketSession session, VoiceChatMessage message) {
        log.info("收到listen消息: sessionId={}, text={}", session.getId(), message.getText());
        // listen消息主要用于文本输入场景，暂不处理
    }

    private void handleAbort(WebSocketSession session, VoiceChatMessage message) {
        String sessionId = session.getId();
        log.info("收到abort消息: sessionId={}, reason={}", sessionId, message.getReason());
        // 清理该session的资源
        audioBuffers.remove(sessionId);
    }

    private void processAudioEnd(WebSocketSession session, AgentContext agentContext) {
        // 处理累积的完整音频
        String sessionId = session.getId();
        byte[] audioData = audioBuffers.get(sessionId);
        
        if (audioData != null && audioData.length > 0) {
            // 直接进行ASR识别
            asrService.recognize(audioData, new AsrService.AsrCallback() {
                @Override
                public void onResult(String text) {
                    sendSttMessage(session, text);
                }
                
                @Override
                public void onError(Throwable t) {
                    sendErrorMessage(session, "ASR识别失败: " + t.getMessage());
                }
            });
        }
        
        audioBuffers.put(sessionId, new byte[0]);
    }

    // ===== 发送消息方法 =====

    private void sendSttMessage(WebSocketSession session, String text) {
        try {
            VoiceChatMessage message = new VoiceChatMessage();
            message.setType("stt");
            message.setText(text);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException e) {
            log.error("发送STT消息失败", e);
        }
    }

    private void sendResponseChunk(WebSocketSession session, ResponseChunk chunk) {
        try {
            if (chunk.getText() != null) {
                // 发送文字消息
                VoiceChatMessage textMessage = new VoiceChatMessage();
                textMessage.setType("llm");
                textMessage.setText(chunk.getText());
                textMessage.setSequence(chunk.getSequence());
                textMessage.setIsFinal(chunk.isFinal());
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(textMessage)));
            }
            
            if (chunk.getAudioData() != null && chunk.getAudioData().length > 0) {
                // 发送语音消息(二进制)
                org.springframework.web.socket.BinaryMessage audioMessage = 
                    new org.springframework.web.socket.BinaryMessage(chunk.getAudioData());
                session.sendMessage(audioMessage);
            }
        } catch (IOException e) {
            log.error("发送响应片段失败", e);
        }
    }

    private void sendErrorMessage(WebSocketSession session, String error) {
        try {
            VoiceChatMessage message = new VoiceChatMessage();
            message.setType("error");
            message.setText(error);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException e) {
            log.error("发送错误消息失败", e);
        }
    }
}
