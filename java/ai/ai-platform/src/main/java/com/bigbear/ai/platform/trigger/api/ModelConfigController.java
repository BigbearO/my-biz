package com.bigbear.ai.platform.trigger.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.bigbear.ai.platform.domain.bo.ModelConfigBo;
import com.bigbear.ai.platform.domain.entity.ModelConfigEntity;
import com.bigbear.ai.platform.domain.vo.ModelConfigVo;
import com.bigbear.ai.platform.infra.manager.ModelConfigManager;
import com.mybatisflex.core.query.QueryWrapper;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;

import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型配置管理接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/modelConfig")
@Tag(name = "模型配置管理", description = "模型配置管理相关接口")
public class ModelConfigController extends BaseController {

    private final ModelConfigManager modelConfigManager;
    private final Converter converter;

    /**
     * 分页查询配置列表
     */
    @Operation(summary = "分页查询配置列表")
    @SaCheckPermission("ai:modelConfig:list")
    @GetMapping("/list")
    public PageResult<ModelConfigVo> list(ModelConfigBo bo, PageQuery pageQuery) {
        return modelConfigManager.selectVoPage(pageQuery, buildQueryWrapper(bo));
    }

    /**
     * 获取配置详情
     */
    @Operation(summary = "获取配置详情")
    @SaCheckPermission("ai:modelConfig:query")
    @GetMapping("/{id}")
    public R<ModelConfigVo> getInfo(@PathVariable String id) {
        return R.ok(modelConfigManager.selectVoById(id));
    }

    /**
     * 根据编码获取配置
     */
    @Operation(summary = "根据编码获取配置")
    @SaCheckPermission("ai:modelConfig:query")
    @GetMapping("/code/{modelCode}")
    public R<ModelConfigVo> getByCode(@PathVariable String modelCode) {
        ModelConfigEntity entity = modelConfigManager.getByModelCode(modelCode);
        return R.ok(converter.convert(entity, ModelConfigVo.class));
    }

    /**
     * 根据模型类型查询配置列表
     */
    @Operation(summary = "根据模型类型查询配置列表")
    @SaCheckPermission("ai:modelConfig:list")
    @GetMapping("/type/{modelType}")
    public R<List<ModelConfigVo>> listByModelType(@PathVariable String modelType) {
        List<ModelConfigEntity> list = modelConfigManager.listByModelType(modelType);
        return R.ok(converter.convert(list, ModelConfigVo.class));
    }

    /**
     * 获取默认配置
     */
    @Operation(summary = "获取默认配置")
    @SaCheckPermission("ai:modelConfig:query")
    @GetMapping("/default/{modelType}")
    public R<ModelConfigVo> getDefaultConfig(@PathVariable String modelType) {
        ModelConfigEntity entity = modelConfigManager.getDefaultConfig(modelType);
        return R.ok(converter.convert(entity, ModelConfigVo.class));
    }

    /**
     * 新增配置
     */
    @Operation(summary = "新增配置")
    @SaCheckPermission("ai:modelConfig:add")
    @Log(title = "模型配置管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ModelConfigBo bo) {
        // 检查编码是否已存在
        if (modelConfigManager.isModelCodeExists(bo.getModelCode())) {
            return R.fail("模型编码已存在");
        }
        ModelConfigEntity entity = converter.convert(bo, ModelConfigEntity.class);
        return toAjax(modelConfigManager.save(entity));
    }

    /**
     * 修改配置
     */
    @Operation(summary = "修改配置")
    @SaCheckPermission("ai:modelConfig:edit")
    @Log(title = "模型配置管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ModelConfigBo bo) {
        ModelConfigEntity entity = converter.convert(bo, ModelConfigEntity.class);
        return toAjax(modelConfigManager.updateById(entity));
    }

    /**
     * 删除配置（软删除）
     */
    @Operation(summary = "删除配置")
    @SaCheckPermission("ai:modelConfig:remove")
    @Log(title = "模型配置管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable List<String> ids) {
        for (String id : ids) {
            modelConfigManager.softDelete(id);
        }
        return R.ok();
    }

    /**
     * 设置默认配置
     */
    @Operation(summary = "设置默认配置")
    @SaCheckPermission("ai:modelConfig:edit")
    @Log(title = "模型配置管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/setDefault/{id}")
    public R<Void> setDefault(@PathVariable String id, @RequestParam String modelType) {
        return toAjax(modelConfigManager.setDefaultConfig(id, modelType));
    }

    /**
     * 启用/禁用配置
     */
    @Operation(summary = "启用/禁用配置")
    @SaCheckPermission("ai:modelConfig:edit")
    @Log(title = "模型配置管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/toggleEnabled/{id}")
    public R<Void> toggleEnabled(@PathVariable String id, @RequestParam Integer isEnabled) {
        return toAjax(modelConfigManager.toggleEnabled(id, isEnabled));
    }

    private QueryWrapper buildQueryWrapper(ModelConfigBo bo) {
        QueryWrapper qw = QueryWrapper.create().from(ModelConfigEntity.class);
        if (StringUtils.isNotBlank(bo.getModelType())) {
            qw.and(ModelConfigEntity::getModelType).eq(bo.getModelType());
        }
        if (StringUtils.isNotBlank(bo.getModelCode())) {
            qw.and(ModelConfigEntity::getModelCode).like(bo.getModelCode());
        }
        if (StringUtils.isNotBlank(bo.getModelName())) {
            qw.and(ModelConfigEntity::getModelName).like(bo.getModelName());
        }
        if (bo.getIsEnabled() != null) {
            qw.and(ModelConfigEntity::getIsEnabled).eq(bo.getIsEnabled());
        }
        qw.and(ModelConfigEntity::getDelFlag).eq(0);
        qw.orderBy(ModelConfigEntity::getSort, true);
        return qw;
    }
}
