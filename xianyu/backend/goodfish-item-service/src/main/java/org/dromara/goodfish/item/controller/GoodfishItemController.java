package org.dromara.goodfish.item.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.dromara.goodfish.item.domain.bo.GoodfishItemCopyPromptBo;
import org.dromara.goodfish.item.domain.bo.GoodfishItemEditBo;
import org.dromara.goodfish.item.domain.bo.GoodfishItemQueryBo;
import org.dromara.goodfish.item.domain.bo.GoodfishItemSyncBo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemSyncVo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;
import org.dromara.goodfish.item.domain.vo.GoodfishPromptTemplateVo;
import org.dromara.goodfish.item.service.IGoodfishItemService;
import org.dromara.goodfish.item.service.IGoodfishPromptTemplateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 闲鱼商品管理（商品服务）.
 *
 * <p>网关将 /goodfish/item/** 去除 /goodfish 前缀后转发，故服务侧基础路径为 /item.</p>
 *
 * <p>权限：后台读写接口按 RuoYi 权限标识校验；客户端同步接口只要求登录态
 * （客户端 token 由网关校验，账号归属校验待账号-用户关系模型确定后补充）.</p>
 *
 * @author goodfish
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/item")
public class GoodfishItemController extends BaseController {

    private final IGoodfishItemService itemService;
    private final IGoodfishPromptTemplateService promptTemplateService;

    /**
     * 商品列表（分页，后台；支持按账号筛选）.
     */
    @SaCheckPermission("goodfish:item:list")
    @GetMapping("/list")
    public R<PageResult<GoodfishItemVo>> list(GoodfishItemQueryBo bo, PageQuery pageQuery) {
        return R.ok(itemService.queryPageList(bo, pageQuery));
    }

    /**
     * 商品详情（含提示词配置）.
     */
    @SaCheckPermission("goodfish:item:query")
    @GetMapping("/{id}")
    public R<GoodfishItemVo> getInfo(@PathVariable Long id) {
        return R.ok(itemService.queryById(id));
    }

    /**
     * 客户端商品同步（全量上报，upsert；只增不删、保留提示词）.
     */
    @SaCheckLogin
    @PostMapping("/client/sync")
    public R<GoodfishItemSyncVo> sync(@Validated @RequestBody GoodfishItemSyncBo bo) {
        return R.ok(itemService.syncItems(bo));
    }

    /**
     * 更新商品 + 提示词配置（后台编辑；库存状态只读）.
     */
    @SaCheckPermission("goodfish:item:edit")
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody GoodfishItemEditBo bo) {
        return toAjax(itemService.editItem(bo));
    }

    /**
     * 删除商品（软删；闲鱼侧仍在的商品再次同步会复活）.
     */
    @SaCheckPermission("goodfish:item:remove")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        return toAjax(itemService.removeItem(id));
    }

    /**
     * 复制提示词到其他商品（覆盖式，可多选目标）.
     */
    @SaCheckPermission("goodfish:item:copyPrompt")
    @PostMapping("/copy-prompt")
    public R<Void> copyPrompt(@Validated @RequestBody GoodfishItemCopyPromptBo bo) {
        return toAjax(itemService.copyPrompt(bo));
    }

    /**
     * 提示词模板列表（只读，来自 Spring 配置）.
     */
    @SaCheckPermission("goodfish:item:list")
    @GetMapping("/template/list")
    public R<List<GoodfishPromptTemplateVo>> templates() {
        return R.ok(promptTemplateService.listTemplates());
    }

    /**
     * 按闲鱼账号 ID 统计商品数（后台统计展示）.
     */
    @SaCheckPermission("goodfish:item:list")
    @GetMapping("/count")
    public R<Long> count(@RequestParam String goodfishAccountId) {
        return R.ok(itemService.countByAccount(goodfishAccountId));
    }

}
