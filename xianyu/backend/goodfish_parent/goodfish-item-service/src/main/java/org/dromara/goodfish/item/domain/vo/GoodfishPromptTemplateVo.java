package org.dromara.goodfish.item.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提示词模板视图对象（系统内置只读，来自 Spring 配置）.
 *
 * @author goodfish
 */
@Data
public class GoodfishPromptTemplateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
