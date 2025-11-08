package com.bigbear.goofish.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.bigbear.ai.biz.domain.bo.ItemBo;
import com.bigbear.ai.biz.domain.vo.ItemVo;
import com.bigbear.ai.biz.service.IItemService;
import com.bigbear.ai.biz.service.ItemChatService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品服务控制器
 */
@RestController
@RequestMapping("/item")
@RequiredArgsConstructor
public class ItemController extends BaseController {

    private final IItemService itemService;

    private final ItemChatService itemChatService;

    /**
     * 查询商品列表
     */
    @SaCheckPermission("ai:item:list")
    @GetMapping("/list")
    public TableDataInfo<ItemVo> list(ItemBo bo, PageQuery pageQuery) {
        return itemService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出商品列表
     */
    @SaCheckPermission("ai:item:export")
    @Log(title = "商品", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ItemBo bo, HttpServletResponse response) {
        List<ItemVo> list = itemService.queryList(bo);
        ExcelUtil.exportExcel(list, "商品", ItemVo.class, response);
    }

    /**
     * 获取商品详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("ai:item:query")
    @GetMapping("/{id}")
    public R<ItemVo> getInfo(@NotNull(message = "主键不能为空")
    @PathVariable("id") Long id) {
        return R.ok(itemService.queryById(id));
    }

    /**
     * 新增商品
     */
    @SaCheckPermission("ai:item:add")
    @Log(title = "商品", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ItemBo bo) {
        return toAjax(itemService.insertByBo(bo));
    }

    /**
     * 修改商品
     */
    @SaCheckPermission("ai:item:edit")
    @Log(title = "商品", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ItemBo bo) {
        return toAjax(itemService.updateByBo(bo));
    }

    /**
     * 删除商品
     *
     * @param ids 主键串
     */
    @SaCheckPermission("ai:item:remove")
    @Log(title = "商品", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
    @PathVariable("ids") Long[] ids) {
        return toAjax(itemService.deleteWithValidByIds(List.of(ids), true));
    }


    /**
     * 商品对话
     */
    @PostMapping("/chat")
    public String chat(@RequestHeader("tenant-id") String tenantId,
        @RequestParam Long itemId,
        @RequestParam String conversationId,
        @RequestBody ChatRequest request) {
        return itemChatService.chat(itemId,conversationId, request.getQuery());
    }

    /**
     * 对话请求参数
     */
    public static class ChatRequest {

        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
}
