package org.dromara.goodfish.item.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示词模板配置属性（系统内置只读）.
 *
 * <p>模板存放在商品服务 Spring 配置文件 {@code goodfish-prompt-templates.yml}，
 * 前缀 {@code goodfish.prompt-templates}；后台不提供模板增删改，
 * 调整模板需改配置文件后随版本发布（见需求-云端-提示词模板）.</p>
 *
 * @author goodfish
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "goodfish.prompt-templates")
public class GoodfishPromptTemplatesProperties {

    /**
     * 模板列表（启动时加载为只读常量）
     */
    private List<Template> templates = new ArrayList<>();

    /**
     * 单个提示词模板.
     */
    @Data
    public static class Template {

        /**
         * 模板编码
         */
        private String code;

        /**
         * 模板名称
         */
        private String name;

        /**
         * 模板内容（提示词正文，含变量占位符）
         */
        private String content;

    }

}
