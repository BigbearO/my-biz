package org.dromara.goodfish.admin.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.goodfish.admin.domain.vo.AdminItemVo;
import org.dromara.goodfish.admin.service.IAdminItemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台商品列表（BFF 跨域聚合）.
 *
 * <p>网关将 /goodfish/admin/** 去除 /goodfish 前缀后转发，故服务侧基础路径为 /admin/item.</p>
 *
 * @author goodfish
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/admin/item")
public class AdminItemController extends BaseController {

    private final IAdminItemService adminItemService;

    /**
     * 商品列表（聚合账号名/昵称；支持按账号筛选、标题模糊）.
     */
    @GetMapping("/list")
    public R<PageResult<AdminItemVo>> list(@RequestParam(required = false) Integer pageNum,
                                           @RequestParam(required = false) Integer pageSize,
                                           @RequestParam(required = false) String goodfishAccountId,
                                           @RequestParam(required = false) String title) {
        return R.ok(adminItemService.queryPageList(pageNum, pageSize, goodfishAccountId, title));
    }

}
