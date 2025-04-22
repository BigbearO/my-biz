package com.bigbear.order.triger.controller;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bigbear.common.core.util.R;
import com.bigbear.common.log.annotation.SysLog;
import com.pig4cloud.plugin.excel.annotation.ResponseExcel;
import com.pig4cloud.plugin.excel.annotation.RequestExcel;
import com.bigbear.order.entity.po.AccountPO;
import com.bigbear.order.manager.AccountManager;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.bigbear.common.security.annotation.HasPermission;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 游戏账号表
 *
 * @author bigbear
 * @date 2025-04-22 21:33:23
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/gameAccount" )
@Tag(description = "gameAccount" , name = "游戏账号表管理" )
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AccountController {

    private final AccountManager accountManager;

    /**
     * 分页查询
     * @param page 分页对象
     * @param account 游戏账号表
     * @return
     */
    @Operation(summary = "分页查询" , description = "分页查询" )
    @GetMapping("/page" )
    @HasPermission("order_gameAccount_view")
    public R getAccountPage(@ParameterObject Page page, @ParameterObject AccountPO account) {
        LambdaQueryWrapper<AccountPO> wrapper = Wrappers.lambdaQuery();
        return R.ok(accountManager.page(page, wrapper));
    }


    /**
     * 通过条件查询游戏账号表
     * @param account 查询条件
     * @return R  对象列表
     */
    @Operation(summary = "通过条件查询" , description = "通过条件查询对象" )
    @GetMapping("/details" )
    @HasPermission("order_gameAccount_view")
    public R getDetails(@ParameterObject AccountPO account) {
        return R.ok(accountManager.list(Wrappers.query(account)));
    }

    /**
     * 新增游戏账号表
     * @param account 游戏账号表
     * @return R
     */
    @Operation(summary = "新增游戏账号表" , description = "新增游戏账号表" )
    @SysLog("新增游戏账号表" )
    @PostMapping
    @HasPermission("order_gameAccount_add")
    public R save(@RequestBody AccountPO account) {
        return R.ok(accountManager.save(account));
    }

    /**
     * 修改游戏账号表
     * @param account 游戏账号表
     * @return R
     */
    @Operation(summary = "修改游戏账号表" , description = "修改游戏账号表" )
    @SysLog("修改游戏账号表" )
    @PutMapping
    @HasPermission("order_gameAccount_edit")
    public R updateById(@RequestBody AccountPO account) {
        return R.ok(accountManager.updateById(account));
    }

    /**
     * 通过id删除游戏账号表
     * @param ids id列表
     * @return R
     */
    @Operation(summary = "通过id删除游戏账号表" , description = "通过id删除游戏账号表" )
    @SysLog("通过id删除游戏账号表" )
    @DeleteMapping
    @HasPermission("order_gameAccount_del")
    public R removeById(@RequestBody Long[] ids) {
        return R.ok(accountManager.removeBatchByIds(CollUtil.toList(ids)));
    }


    /**
     * 导出excel 表格
     * @param account 查询条件
   	 * @param ids 导出指定ID
     * @return excel 文件流
     */
    @ResponseExcel
    @GetMapping("/export")
    @HasPermission("order_gameAccount_export")
    public List<AccountPO> exportExcel(AccountPO account, Long[] ids) {
        return accountManager.list(Wrappers.lambdaQuery(account).in(ArrayUtil.isNotEmpty(ids), AccountPO::getId, ids));
    }

    /**
     * 导入excel 表
     * @param accountList 对象实体列表
     * @param bindingResult 错误信息列表
     * @return ok fail
     */
    @PostMapping("/import")
    @HasPermission("order_gameAccount_export")
    public R importExcel(@RequestExcel List<AccountPO> accountList, BindingResult bindingResult) {
        return R.ok(accountManager.saveBatch(accountList));
    }
}
