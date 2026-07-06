//package com.bigbear.goofish.api;
//
//import java.util.List;
//
//import lombok.RequiredArgsConstructor;
//import jakarta.servlet.http.HttpServletResponse;
//import jakarta.validation.constraints.*;
//import cn.dev33.satoken.annotation.SaCheckPermission;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.validation.annotation.Validated;
//import org.dromara.common.redis.annotation.RepeatSubmit;
//import org.dromara.common.log.annotation.Log;
//import org.dromara.common.web.core.BaseController;
//import org.dromara.common.mybatis.core.page.PageQuery;
//import org.dromara.common.core.domain.R;
//import org.dromara.common.core.validate.AddGroup;
//import org.dromara.common.core.validate.EditGroup;
//import org.dromara.common.log.enums.BusinessType;
//import org.dromara.common.excel.utils.ExcelUtil;
//import com.xxs.ai.domain.vo.AccountVo;
//import com.xxs.ai.domain.bo.AccountBo;
//import com.xxs.ai.service.IAccountService;
//
//
///**
// * 账号
// * 前端访问路由地址为:/ai/account
// *
// * @author LionLi
// * @date 2025-11-09
// */
//@Validated
//@RequiredArgsConstructor
//@RestController
//@RequestMapping("/account")
//public class AccountController2 extends BaseController {
//
//    private final IAccountService accountService;
//
//    /**
//     * 查询账号列表
//     */
//    @SaCheckPermission("ai:account:list")
//    @GetMapping("/list")
//    public TableDataInfo<AccountVo> list(AccountBo bo, PageQuery pageQuery) {
//        return accountService.queryPageList(bo, pageQuery);
//    }
//
//    /**
//     * 导出账号列表
//     */
//    @SaCheckPermission("ai:account:export")
//    @Log(title = "账号", businessType = BusinessType.EXPORT)
//    @PostMapping("/export")
//    public void export(AccountBo bo, HttpServletResponse response) {
//        List<AccountVo> list = accountService.queryList(bo);
//        ExcelUtil.exportExcel(list, "账号", AccountVo.class, response);
//    }
//
//    /**
//     * 获取账号详细信息
//     *
//     * @param id 主键
//     */
//    @SaCheckPermission("ai:account:query")
//    @GetMapping("/{id}")
//    public R<AccountVo> getInfo(@NotNull(message = "主键不能为空")
//                                     @PathVariable("id") Long id) {
//        return R.ok(accountService.queryById(id));
//    }
//
//    /**
//     * 新增账号
//     */
//    @SaCheckPermission("ai:account:add")
//    @Log(title = "账号", businessType = BusinessType.INSERT)
//    @RepeatSubmit()
//    @PostMapping()
//    public R<Void> add(@Validated(AddGroup.class) @RequestBody AccountBo bo) {
//        return toAjax(accountService.insertByBo(bo));
//    }
//
//    /**
//     * 修改账号
//     */
//    @SaCheckPermission("ai:account:edit")
//    @Log(title = "账号", businessType = BusinessType.UPDATE)
//    @RepeatSubmit()
//    @PutMapping()
//    public R<Void> edit(@Validated(EditGroup.class) @RequestBody AccountBo bo) {
//        return toAjax(accountService.updateByBo(bo));
//    }
//
//    /**
//     * 删除账号
//     *
//     * @param ids 主键串
//     */
//    @SaCheckPermission("ai:account:remove")
//    @Log(title = "账号", businessType = BusinessType.DELETE)
//    @DeleteMapping("/{ids}")
//    public R<Void> remove(@NotEmpty(message = "主键不能为空")
//                          @PathVariable("ids") Long[] ids) {
//        return toAjax(accountService.deleteWithValidByIds(List.of(ids), true));
//    }
//}
