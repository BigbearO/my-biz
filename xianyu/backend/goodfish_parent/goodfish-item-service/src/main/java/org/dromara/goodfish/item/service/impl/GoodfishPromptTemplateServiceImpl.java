package org.dromara.goodfish.item.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.goodfish.item.config.GoodfishPromptTemplatesProperties;
import org.dromara.goodfish.item.domain.vo.GoodfishPromptTemplateVo;
import org.dromara.goodfish.item.service.IGoodfishPromptTemplateService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提示词模板Service业务层实现.
 *
 * <p>模板为系统内置只读：存放在 Spring 配置文件 goodfish-prompt-templates.yml，
 * 启动时加载；后台不提供增删改，调整模板需改配置后随版本发布.</p>
 *
 * @author goodfish
 */
@Service
@RequiredArgsConstructor
public class GoodfishPromptTemplateServiceImpl implements IGoodfishPromptTemplateService {

    private final GoodfishPromptTemplatesProperties templatesProperties;

    /**
     * 查询内置提示词模板列表.
     */
    @Override
    public List<GoodfishPromptTemplateVo> listTemplates() {
        return templatesProperties.getTemplates().stream()
            .map(template -> {
                GoodfishPromptTemplateVo vo = new GoodfishPromptTemplateVo();
                vo.setCode(template.getCode());
                vo.setName(template.getName());
                vo.setContent(template.getContent());
                return vo;
            })
            .toList();
    }

}
