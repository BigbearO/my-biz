package com.bigbear.mall.biz.trigger.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.collection.CollUtil;
import com.bigbear.mall.biz.application.OrderApplicationService;
import com.bigbear.mall.biz.domain.dto.OrderImportDTO;
import com.bigbear.mall.biz.domain.dto.OrderImportResultDTO;
import com.bigbear.mall.biz.domain.dto.RowErrorDTO;
import com.bigbear.mall.biz.domain.entity.OrderEntity;
import com.bigbear.mall.biz.listener.OrderImportListener;
import com.bigbear.mall.biz.manager.IOrderManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单表管理控制器
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/order")
@Tag(description = "订单表管理", name = "订单表管理")
public class OrderController extends BaseController {

    private final IOrderManager orderManager;
    private final OrderApplicationService orderApplicationService;

    /**
     * 测试MQ发送
     */
    @Operation(summary = "测试MQ发送", description = "测试MQ发送")
    @GetMapping("/test")
    public R<Void> test() {
        // TODO: 注入 RocketMQTemplate 后发送消息
        // rocketMQTemplate.convertAndSend("games:0", "111");
        return R.ok();
    }

    /**
     * 分页查询订单列表
     */
    @Operation(summary = "分页查询", description = "分页查询订单列表")
    @SaCheckPermission("mall:order:list")
    @GetMapping("/list")
    public TableDataInfo<OrderEntity> page(OrderEntity order, PageQuery pageQuery) {
        val data = orderManager.selectPageList(order, pageQuery);

        return data;
    }

    /**
     * 通过条件查询订单列表
     */
//    @Operation(summary = "通过条件查询", description = "通过条件查询订单列表")
//    @SaCheckPermission("mall:order:list")
//    @GetMapping("/list")
//    public R<List<OrderEntity>> list(OrderEntity order) {
//        return R.ok(orderManager.selectList(order));
//    }

    /**
     * 获取订单详情
     */
    @Operation(summary = "获取订单详情", description = "获取订单详情")
    @SaCheckPermission("mall:order:query")
    @GetMapping("/{id}")
    public R<OrderEntity> getInfo(@PathVariable Long id) {
        return R.ok(orderManager.getById(id));
    }

    /**
     * 新增订单
     * 自动处理账号逻辑：
     * 1. 账号不存在则新增（startTime=今天，expireTime=今天+会员天数）
     * 2. 账号存在且expireTime>今天，startTime不变，expireTime=今天+会员天数
     * 3. 账号存在且expireTime<=今天，startTime=今天，expireTime累加
     */
    @Operation(summary = "新增订单", description = "新增订单，自动处理账号会员时间")
    @SaCheckPermission("mall:order:add")
    @Log(title = "订单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody OrderEntity order) {
        orderApplicationService.addOrderWithAccount(order);
        return R.ok();
    }

    /**
     * 修改订单
     */
    @Operation(summary = "修改订单", description = "修改订单")
    @SaCheckPermission("mall:order:edit")
    @Log(title = "订单管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody OrderEntity order) {
        orderManager.updateById(order);
        return R.ok();
    }

    /**
     * 删除订单
     */
    @Operation(summary = "删除订单", description = "删除订单")
    @SaCheckPermission("mall:order:remove")
    @Log(title = "订单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        orderManager.removeByIds(CollUtil.toList(ids));
        return R.ok();
    }

    /**
     * 导出订单到Excel
     */
//    @Operation(summary = "导出订单", description = "导出订单到Excel")
//    @SaCheckPermission("mall:order:export")
//    @Log(title = "订单管理", businessType = BusinessType.EXPORT)
//    @PostMapping("/export")
//    public void export(OrderEntity order, HttpServletResponse response) {
//        List<OrderEntity> list = orderManager.selectList(order);
//        List<OrderExcelDTO> excelList = list.stream()
//                .map(item -> BeanUtil.copyProperties(item, OrderExcelDTO.class))
//                .toList();
//        ExcelUtil.exportExcel(response, excelList, "订单数据", OrderExcelDTO.class);
//    }

    /**
     * Excel导入订单（使用EasyExcel）
     * 导入时自动处理账号逻辑：
     * 1. 账号不存在则新增
     * 2. 账号存在且expireTime>=今天，更新expireTime，startTime不变
     * 3. 账号存在且expireTime<今天，重置startTime为今天，expireTime累加
     */
    @Operation(summary = "Excel导入订单", description = "Excel导入订单，自动处理账号会员时间")
    @SaCheckPermission("mall:order:add")
    @Log(title = "订单管理", businessType = BusinessType.IMPORT)
    @PostMapping(value = "/importByExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<OrderImportResultDTO> importByExcel(@RequestPart("file") MultipartFile file) throws IOException {
        // 1. 使用EasyExcel解析
        OrderImportListener listener = new OrderImportListener();
        val orderImportDTOExcelResult = ExcelUtil.importExcel(file.getInputStream(), OrderImportDTO.class, listener);

        // 2. 获取解析后的数据
        List<OrderImportDTO> list = listener.getList();

        // 3. 执行业务导入
        OrderImportResultDTO result = orderApplicationService.importOrdersWithAccount(list);

        // 4. 合并Excel解析错误
        if (!listener.getErrorList().isEmpty()) {
            List<RowErrorDTO> allErrors = new ArrayList<>(result.getErrors());
            allErrors.addAll(listener.getErrorList());
            result.setErrors(allErrors);
            result.setFailCount(allErrors.size());
        }

        // 5. 返回结果
        if (result.getFailCount() > 0) {
            return R.fail("导入完成，成功" + result.getSuccessCount()
                    + "条，失败" + result.getFailCount() + "条", result);
        }

        return R.ok(result);
    }

    /**
     * 下载导入模板
     */
    @Operation(summary = "下载导入模板", description = "下载订单导入模板")
    @SaCheckPermission("mall:order:add")
    @GetMapping("/importTemplate")
    public void downloadTemplate(HttpServletResponse response) {
        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("订单导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 写入响应流
        ExcelUtil.exportExcel(new ArrayList<>(), "订单", OrderImportDTO.class, response);
    }

    private OrderImportDTO createTemplateRow(String account, Integer gameType, Integer days) {
        OrderImportDTO dto = new OrderImportDTO();
        dto.setAccount(account);
        dto.setGameType(gameType);
        dto.setDays(days);
        return dto;
    }
}
