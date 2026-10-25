package org.dromara.goodfish.item.service;

import org.dromara.goodfish.item.domain.vo.GoodfishPromptTemplateVo;

import java.util.List;

/**
 * 提示词模板Service接口（系统内置只读）.
 *
 * @author goodfish
 */
public interface IGoodfishPromptTemplateService {

    /**
     * 查询内置提示词模板列表（只读，来自 Spring 配置）.
     */
    List<GoodfishPromptTemplateVo> listTemplates();

}
