package com.bigbear.ai.platform.trigger.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 敏感词拦截器
 */
@Slf4j
@Component
public class SensitiveWordInterceptor {

    // 敏感词库（实际项目中应该从数据库或配置加载）
    private static final Set<String> SENSITIVE_WORDS = new HashSet<>(Arrays.asList(
            "敏感词1",
            "敏感词2",
            "敏感词3"
            // TODO: 从数据库加载敏感词库
    ));

    /**
     * 检查文本是否包含敏感词
     * 
     * @param text 待检查文本
     * @return 是否包含敏感词
     */
    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        for (String word : SENSITIVE_WORDS) {
            if (text.contains(word)) {
                log.warn("检测到敏感词: {}", word);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 查找第一个敏感词
     * 
     * @param text 待检查文本
     * @return 第一个敏感词，未找到返回null
     */
    public String findFirst(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        for (String word : SENSITIVE_WORDS) {
            if (text.contains(word)) {
                return word;
            }
        }
        
        return null;
    }
    
    /**
     * 查找所有敏感词
     * 
     * @param text 待检查文本
     * @return 所有敏感词列表
     */
    public List<String> findAll(String text) {
        List<String> found = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return found;
        }
        
        for (String word : SENSITIVE_WORDS) {
            if (text.contains(word)) {
                found.add(word);
            }
        }
        
        return found;
    }
    
    /**
     * 替换敏感词
     * 
     * @param text 待处理文本
     * @param replaceChar 替换字符
     * @return 替换后的文本
     */
    public String replace(String text, char replaceChar) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String result = text;
        for (String word : SENSITIVE_WORDS) {
            result = result.replace(word, String.valueOf(replaceChar).repeat(word.length()));
        }
        
        return result;
    }
    
    /**
     * 默认替换为*
     */
    public String replace(String text) {
        return replace(text, '*');
    }
    
    /**
     * 加载敏感词库
     * 
     * @param words 敏感词列表
     */
    public void loadWords(Collection<String> words) {
        SENSITIVE_WORDS.clear();
        SENSITIVE_WORDS.addAll(words);
        log.info("敏感词库已更新: count={}", SENSITIVE_WORDS.size());
    }
    
    /**
     * 添加敏感词
     * 
     * @param word 敏感词
     */
    public void addWord(String word) {
        SENSITIVE_WORDS.add(word);
    }
    
    /**
     * 移除敏感词
     * 
     * @param word 敏感词
     */
    public void removeWord(String word) {
        SENSITIVE_WORDS.remove(word);
    }
    
    /**
     * 获取敏感词数量
     * 
     * @return 敏感词数量
     */
    public int getWordCount() {
        return SENSITIVE_WORDS.size();
    }
}
