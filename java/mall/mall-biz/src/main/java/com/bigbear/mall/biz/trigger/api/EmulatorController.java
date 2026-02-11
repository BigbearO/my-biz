package com.bigbear.mall.biz.trigger.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.collection.CollUtil;
import com.bigbear.mall.biz.domain.entity.EmulatorEntity;
import com.bigbear.mall.biz.domain.vo.AvailableEmulatorVO;
import com.bigbear.mall.biz.manager.IEmulatorManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模拟器管理控制器
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emulator")
@Tag(name = "模拟器管理", description = "模拟器管理")
public class EmulatorController extends BaseController {

    private final IEmulatorManager emulatorManager;

    /**
     * 分页查询模拟器列表
     */
    @Operation(summary = "分页查询", description = "分页查询模拟器列表")
    @SaCheckPermission("mall:emulator:list")
    @GetMapping("/list")
    public TableDataInfo<EmulatorEntity> page(EmulatorEntity emulator, PageQuery pageQuery) {
        return emulatorManager.selectPageList(emulator, pageQuery);
    }

    /**
     * 获取模拟器详情
     */
    @Operation(summary = "获取详情", description = "获取模拟器详情")
    @SaCheckPermission("mall:emulator:query")
    @GetMapping("/{id}")
    public R<EmulatorEntity> getInfo(@PathVariable Long id) {
        return R.ok(emulatorManager.getById(id));
    }

    /**
     * 新增模拟器
     */
    @Operation(summary = "新增", description = "新增模拟器")
    @SaCheckPermission("mall:emulator:add")
    @Log(title = "模拟器管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody @Valid EmulatorEntity emulator) {
        emulatorManager.save(emulator);
        return R.ok();
    }

    /**
     * 修改模拟器
     */
    @Operation(summary = "修改", description = "修改模拟器")
    @SaCheckPermission("mall:emulator:edit")
    @Log(title = "模拟器管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody @Valid EmulatorEntity emulator) {
        emulatorManager.updateById(emulator);
        return R.ok();
    }

    /**
     * 修改模拟器
     */
    @Operation(summary = "修改", description = "修改模拟器")
    @SaCheckPermission("mall:emulator:edit")
    @Log(title = "模拟器管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody @Valid EmulatorEntity emulator) {
        emulatorManager.updateById(emulator);
        return R.ok();
    }

    /**
     * 删除模拟器
     */
    @Operation(summary = "删除", description = "删除模拟器")
    @SaCheckPermission("mall:emulator:remove")
    @Log(title = "模拟器管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        emulatorManager.removeByIds(CollUtil.toList(ids));
        return R.ok();
    }

    /**
     * 查询可用模拟器列表
     */
    @Operation(summary = "查询可用模拟器", description = "根据游戏类型查询可用模拟器列表")
    @SaCheckPermission("mall:emulator:list")
    @GetMapping("/available")
    public R<List<AvailableEmulatorVO>> available(@RequestParam Integer gameType) {
        return R.ok(emulatorManager.selectAvailableEmulators(gameType));
    }
}
