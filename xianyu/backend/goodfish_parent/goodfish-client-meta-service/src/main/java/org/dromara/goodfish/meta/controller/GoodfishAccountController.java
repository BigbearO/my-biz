package org.dromara.goodfish.meta.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.dromara.goodfish.meta.domain.bo.GoodfishAccountBo;
import org.dromara.goodfish.meta.domain.bo.GoodfishAccountReportBo;
import org.dromara.goodfish.meta.domain.bo.GoodfishAccountStatusBo;
import org.dromara.goodfish.meta.domain.bo.GoodfishListenLeaseBo;
import org.dromara.goodfish.meta.domain.vo.GoodfishAccountVo;
import org.dromara.goodfish.meta.service.IGoodfishAccountService;
import org.dromara.goodfish.meta.service.IListenLeaseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 闲鱼账号管理（客户端元数据服务）.
 *
 * <p>网关将 /goodfish/meta/** 去除 /goodfish 前缀后转发，故服务侧基础路径为 /meta/account.</p>
 *
 * @author goodfish
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/meta/account")
public class GoodfishAccountController extends BaseController {

    private final IGoodfishAccountService accountService;
    private final IListenLeaseService listenLeaseService;

    /**
     * 账号列表（分页，后台）.
     */
    @GetMapping("/list")
    public R<PageResult<GoodfishAccountVo>> list(GoodfishAccountBo bo, PageQuery pageQuery) {
        return R.ok(accountService.queryPageList(bo, pageQuery));
    }

    /**
     * 账号详情（后台）.
     */
    @GetMapping("/{id}")
    public R<GoodfishAccountVo> getInfo(@PathVariable Long id) {
        return R.ok(accountService.queryById(id));
    }

    /**
     * 客户端账号上报（按 goodfish_id upsert，不接收 Cookie）.
     */
    @PostMapping("/report")
    public R<GoodfishAccountVo> report(@Validated(AddGroup.class) @RequestBody GoodfishAccountReportBo bo) {
        return R.ok(accountService.reportAccount(bo));
    }

    /**
     * 修改账号备注（后台）.
     */
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody GoodfishAccountBo bo) {
        return toAjax(accountService.updateRemark(bo));
    }

    /**
     * 启用 / 停用账号（后台）.
     *
     * <p>★账号只停用、不删除，不提供 DELETE 接口.</p>
     */
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @Validated @RequestBody GoodfishAccountStatusBo bo) {
        bo.setId(id);
        return toAjax(accountService.updateStatus(bo.getId(), bo.getStatus()));
    }

    /**
     * 申请监听占用（客户端「开始监听」前调用；已被其他设备占用 → 40901）.
     */
    @PostMapping("/{goodfishId}/listen/acquire")
    public R<Integer> acquire(@PathVariable String goodfishId, @Validated @RequestBody GoodfishListenLeaseBo bo) {
        return R.ok(listenLeaseService.acquire(goodfishId, bo.getClientInstanceId()));
    }

    /**
     * 续期监听租约（监听期间每 30s 调用，仅持有者可续）.
     */
    @PostMapping("/{goodfishId}/listen/renew")
    public R<Void> renew(@PathVariable String goodfishId, @Validated @RequestBody GoodfishListenLeaseBo bo) {
        listenLeaseService.renew(goodfishId, bo.getClientInstanceId());
        return R.ok();
    }

    /**
     * 释放监听占用（停止监听 / 退出应用时调用，幂等）.
     */
    @DeleteMapping("/{goodfishId}/listen/release")
    public R<Void> release(@PathVariable String goodfishId, @Validated @RequestBody GoodfishListenLeaseBo bo) {
        listenLeaseService.release(goodfishId, bo.getClientInstanceId());
        return R.ok();
    }

}
