package com.bigbear.order.triger.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bigbear.order.infrastructure.OrderManager;
import com.bigbear.order.infrastructure.entity.dto.OrderImportDTO;
import com.bigbear.order.infrastructure.entity.po.OrderPO;
import com.bigbear.order.service.OrderService;
import com.pig4cloud.pig.common.core.exception.ErrorCodes;
import com.pig4cloud.pig.common.core.util.MsgUtils;
import com.pig4cloud.pig.common.core.util.R;
import com.pig4cloud.pig.common.log.annotation.SysLog;
import com.pig4cloud.pig.common.security.annotation.HasPermission;
import com.pig4cloud.plugin.excel.annotation.RequestExcel;
import com.pig4cloud.plugin.excel.annotation.ResponseExcel;
import com.pig4cloud.plugin.excel.vo.ErrorMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 订单表
 *
 * @author xxs
 * @date 2025-04-21 22:32:38
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/order" )
@Tag(description = "order" , name = "订单表管理" )
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class orderController {

    private final OrderManager orderManager;

    private final OrderService orderService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param order 订单表
     * @return
     */
    @Operation(summary = "分页查询" , description = "分页查询" )
    @GetMapping("/page" )
    @HasPermission("order_order_view")
    public R getorderPage(@ParameterObject Page page, @ParameterObject OrderPO order) {
        LambdaQueryWrapper<OrderPO> wrapper = Wrappers.lambdaQuery();
        return R.ok(orderManager.page(page, wrapper));
    }


    /**
     * 通过条件查询订单表
     * @param order 查询条件
     * @return R  对象列表
     */
    @Operation(summary = "通过条件查询" , description = "通过条件查询对象" )
    @GetMapping("/details" )
    @HasPermission("order_order_view")
    public R getDetails(@ParameterObject OrderPO order) {
        return R.ok(orderManager.list(Wrappers.query(order)));
    }

    /**
     * 新增订单表
     * @param order 订单表
     * @return R
     */
    @Operation(summary = "新增订单表" , description = "新增订单表" )
    @SysLog("新增订单表" )
    @PostMapping
    @HasPermission("order_order_add")
    public R save(@RequestBody OrderPO order) {
        return R.ok(orderManager.save(order));
    }


    @Operation(summary = "新增订单表" , description = "新增订单表" )
    @SysLog("新增订单表" )
    @PostMapping("/importExcel")
    @HasPermission("order_order_add")
    public R importByExcel(@RequestExcel List<OrderImportDTO> orderList, BindingResult bindingResult) {


        List<ErrorMessage> errorMessageList = orderService.importByExcel(orderList, bindingResult);
        if (CollUtil.isNotEmpty(errorMessageList)) {
            return R.failed(errorMessageList);
        }

        return R.ok();
    }

    /**
     * 修改订单表
     * @param order 订单表
     * @return R
     */
    @Operation(summary = "修改订单表" , description = "修改订单表" )
    @SysLog("修改订单表" )
    @PutMapping
    @HasPermission("order_order_edit")
    public R updateById(@RequestBody OrderPO order) {
        return R.ok(orderManager.updateById(order));
    }

    /**
     * 通过id删除订单表
     * @param ids id列表
     * @return R
     */
    @Operation(summary = "通过id删除订单表" , description = "通过id删除订单表" )
    @SysLog("通过id删除订单表" )
    @DeleteMapping
    @HasPermission("order_order_del")
    public R removeById(@RequestBody Long[] ids) {
        return R.ok(orderManager.removeBatchByIds(CollUtil.toList(ids)));
    }


    /**
     * 导出excel 表格
     * @param order 查询条件
   	 * @param ids 导出指定ID
     * @return excel 文件流
     */
    @ResponseExcel
    @GetMapping("/export")
    @HasPermission("order_order_export")
    public List<OrderPO> exportExcel(OrderPO order,Long[] ids) {
        return orderManager.list(Wrappers.lambdaQuery(order).in(ArrayUtil.isNotEmpty(ids), OrderPO::getId, ids));
    }

    /**
     * 导入excel 表
     * @param orderList 对象实体列表
     * @param bindingResult 错误信息列表
     * @return ok fail
     */
    @PostMapping("/import")
    @HasPermission("order_order_export")
    public R importExcel(@RequestExcel List<OrderPO> orderList, BindingResult bindingResult) {
        return R.ok(orderManager.saveBatch(orderList));
    }
}
