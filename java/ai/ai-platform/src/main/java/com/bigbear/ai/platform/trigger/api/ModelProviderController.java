package com.bigbear.ai.platform.trigger.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.bigbear.ai.platform.domain.bo.ModelProviderBo;
import com.bigbear.ai.platform.domain.entity.ModelProviderEntity;
import com.bigbear.ai.platform.domain.vo.ModelProviderVo;
import com.bigbear.ai.platform.infra.manager.ModelProviderManager;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型供应商管理接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/modelProvider")
@Tag(name = "模型供应商管理", description = "模型供应商配置管理相关接口")
public class ModelProviderController extends BaseController {

    private final ModelProviderManager modelProviderManager;
    private final Converter converter;

    /**
     * 分页查询供应商列表
     */
    @Operation(summary = "分页查询供应商列表")
    @SaCheckPermission("ai:modelProvider:list")
    @GetMapping("/list")
    public TableDataInfo<ModelProviderVo> list(ModelProviderBo bo, PageQuery pageQuery) {
        return modelProviderManager.selectVoPage(pageQuery, buildQueryWrapper(bo));
    }

    /**
     * 获取供应商详情
     */
    @Operation(summary = "获取供应商详情")
    @SaCheckPermission("ai:modelProvider:query")
    @GetMapping("/{id}")
    public R<ModelProviderVo> getInfo(@PathVariable String id) {
        return R.ok(modelProviderManager.selectVoById(id));
    }

    /**
     * 根据编码获取供应商
     */
    @Operation(summary = "根据编码获取供应商")
    @SaCheckPermission("ai:modelProvider:query")
    @GetMapping("/code/{providerCode}")
    public R<ModelProviderVo> getByCode(@PathVariable String providerCode) {
        ModelProviderEntity entity = modelProviderManager.getByProviderCode(providerCode);
        return R.ok(converter.convert(entity, ModelProviderVo.class));
    }

    /**
     * 根据模型类型查询供应商列表
     */
    @Operation(summary = "根据模型类型查询供应商列表")
    @SaCheckPermission("ai:modelProvider:list")
    @GetMapping("/type/{modelType}")
    public R<List<ModelProviderVo>> listByModelType(@PathVariable String modelType) {
        List<ModelProviderEntity> list = modelProviderManager.listByModelType(modelType);
        return R.ok(converter.convert(list, ModelProviderVo.class));
    }

    /**
     * 新增供应商
     */
    @Operation(summary = "新增供应商")
    @SaCheckPermission("ai:modelProvider:add")
    @Log(title = "模型供应商管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ModelProviderBo bo) {
        // 检查编码是否已存在
        if (modelProviderManager.isProviderCodeExists(bo.getProviderCode())) {
            return R.fail("供应器编码已存在");
        }
        ModelProviderEntity entity = converter.convert(bo, ModelProviderEntity.class);
        return toAjax(modelProviderManager.save(entity));
    }

    /**
     * 修改供应商
     */
    @Operation(summary = "修改供应商")
    @SaCheckPermission("ai:modelProvider:edit")
    @Log(title = "模型供应商管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ModelProviderBo bo) {
        ModelProviderEntity entity = converter.convert(bo, ModelProviderEntity.class);
        return toAjax(modelProviderManager.updateById(entity));
    }

    /**
     * 删除供应商（软删除）
     */
    @Operation(summary = "删除供应商")
    @SaCheckPermission("ai:modelProvider:remove")
    @Log(title = "模型供应商管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable List<String> ids) {
        for (String id : ids) {
            modelProviderManager.softDelete(id);
        }
        return R.ok();
    }

    private com.baomidou.mybatisplus.core.conditions.Wrapper<ModelProviderEntity> buildQueryWrapper(ModelProviderBo bo) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelProviderEntity> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(org.dromara.common.core.utils.StringUtils.isNotBlank(bo.getModelType()), ModelProviderEntity::getModelType, bo.getModelType())
               .like(org.dromara.common.core.utils.StringUtils.isNotBlank(bo.getProviderCode()), ModelProviderEntity::getProviderCode, bo.getProviderCode())
               .like(org.dromara.common.core.utils.StringUtils.isNotBlank(bo.getName()), ModelProviderEntity::getName, bo.getName())
               .eq(ModelProviderEntity::getDelFlag, 0)
               .orderByAsc(ModelProviderEntity::getSort);
        return wrapper;
    }
}
