package com.bigbear.ai.platform.infra.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 音频数据处理器
 */
@Slf4j
@Component
public class AudioDataHandler {

    // 采样率
    private static final int SAMPLE_RATE = 16000;
    // 声道数
    private static final int CHANNELS = 1;
    // 帧大小
    private static final int FRAME_SIZE = 960;

    /**
     * 处理到的Opus音频接收数据
     * 
     * @param opusData Opus编码的音频数据
     * @return PCM数据
     */
    public byte[] processOpusData(byte[] opusData) {
        if (opusData == null || opusData.length == 0) {
            return new byte[0];
        }
        
        try {
            // TODO: Opus解码
            // 这里先模拟返回PCM数据
            log.debug("处理Opus数据: length={}", opusData.length);
            return opusData;
            
        } catch (Exception e) {
            log.error("Opus解码失败", e);
            throw new RuntimeException("Opus解码失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将PCM数据编码为Opus
     * 
     * @param pcmData PCM数据
     * @return Opus编码数据
     */
    public byte[] encodeToOpus(byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            return new byte[0];
        }
        
        try {
            // TODO: Opus编码
            log.debug("编码为Opus: length={}", pcmData.length);
            return pcmData;
            
        } catch (Exception e) {
            log.error("Opus编码失败", e);
            throw new RuntimeException("Opus编码失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将byte数组转换为float数组(16bit PCM)
     * 
     * @param data 字节数组
     * @return float数组
     */
    public float[] bytesToFloats(byte[] data) {
        float[] floats = new float[data.length / 2];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = ((data[i * 2] & 0xff) | (data[i * 2 + 1] << 8)) / 32767.0f;
        }
        return floats;
    }
    
    /**
     * 将float数组转换为byte数组(16bit PCM)
     * 
     * @param floats float数组
     * @return 字节数组
     */
    public byte[] floatsToBytes(float[] floats) {
        byte[] data = new byte[floats.length * 2];
        for (int i = 0; i < floats.length; i++) {
            short sample = (short) (floats[i] * 32767);
            data[i * 2] = (byte) (sample & 0xff);
            data[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        return data;
    }
    
    /**
     * 合并音频数据
     * 
     * @param chunks 音频片段数组
     * @return 合并后的音频数据
     */
    public byte[] mergeAudio(byte[]... chunks) {
        int totalLength = 0;
        for (byte[] chunk : chunks) {
            if (chunk != null) {
                totalLength += chunk.length;
            }
        }
        
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] chunk : chunks) {
            if (chunk != null) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
        }
        
        return result;
    }
}
