package com.bigbear.mall.biz.trigger.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.collection.CollUtil;
import com.bigbear.mall.biz.application.service.AccountEnableService;
import com.bigbear.mall.biz.application.service.AccountImportService;
import com.bigbear.mall.biz.domain.dto.AccountEnableDTO;
import com.bigbear.mall.biz.domain.dto.AccountImportDTO;
import com.bigbear.mall.biz.domain.dto.AccountImportResultDTO;
import com.bigbear.mall.biz.domain.dto.RowErrorDTO;
import com.bigbear.mall.biz.domain.entity.AccountEntity;
import com.bigbear.mall.biz.listener.AccountImportListener;
import com.bigbear.mall.biz.manager.IAccountManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 游戏账号表管理控制器
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/gameAccount")
@Tag(name = "游戏账号表管理", description = "游戏账号表管理")
public class AccountController extends BaseController {

    private final IAccountManager accountManager;
    private final AccountEnableService accountEnableService;
    private final AccountImportService accountImportService;

    /**
     * 分页查询游戏账号列表
     */
    @Operation(summary = "分页查询", description = "分页查询游戏账号列表")
    @SaCheckPermission("mall:gameAccount:list")
    @GetMapping("/list")
    public TableDataInfo<AccountEntity> page(AccountEntity account, PageQuery pageQuery) {
        return accountManager.selectPageList(account, pageQuery);
    }

    /**
     * 通过条件查询游戏账号列表
     */
//    @Operation(summary = "通过条件查询", description = "通过条件查询游戏账号列表")
//    @SaCheckPermission("mall:gameAccount:list")
//    @GetMapping("/list")
//    public R<List<AccountEntity>> list(AccountEntity account) {
//        return R.ok(accountManager.selectList(account));
//    }

    /**
     * 获取游戏账号详情
     */
    @Operation(summary = "获取游戏账号详情", description = "获取游戏账号详情")
    @SaCheckPermission("mall:gameAccount:query")
    @GetMapping("/{id}")
    public R<AccountEntity> getInfo(@PathVariable Long id) {
        return R.ok(accountManager.getById(id));
    }

    /**
     * 新增游戏账号
     */
    @Operation(summary = "新增游戏账号", description = "新增游戏账号")
    @SaCheckPermission("mall:gameAccount:add")
    @Log(title = "游戏账号管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody AccountEntity account) {
        accountManager.save(account);
        return R.ok();
    }

    /**
     * 修改游戏账号
     */
    @Operation(summary = "修改游戏账号", description = "修改游戏账号")
    @SaCheckPermission("mall:gameAccount:edit")
    @Log(title = "游戏账号管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody AccountEntity account) {
        accountManager.updateById(account);
        return R.ok();
    }

    /**
     * 删除游戏账号
     */
    @Operation(summary = "删除游戏账号", description = "删除游戏账号")
    @SaCheckPermission("mall:gameAccount:remove")
    @Log(title = "游戏账号管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        accountManager.removeByIds(CollUtil.toList(ids));
        return R.ok();
    }
//
//    /**
//     * 导出游戏账号到Excel
//     */
//    @Operation(summary = "导出游戏账号", description = "导出游戏账号到Excel")
//    @SaCheckPermission("mall:gameAccount:export")
//    @Log(title = "游戏账号管理", businessType = BusinessType.EXPORT)
//    @PostMapping("/export")
//    public void export(AccountEntity account, HttpServletResponse response) {
//        List<AccountEntity> list = accountManager.selectList(account);
//        ExcelUtil.exportExcel(response, list, "游戏账号数据", AccountEntity.class);
//    }

    /**
     * 启用账号并分配模拟器
     */
    @Operation(summary = "启用账号", description = "启用账号并分配模拟器")
    @SaCheckPermission("mall:gameAccount:enable")
    @Log(title = "游戏账号管理", businessType = BusinessType.UPDATE)
    @PostMapping("/enable")
    public R<Void> enable(@RequestBody @Valid AccountEnableDTO dto) {
        accountEnableService.enableAccount(dto);
        return R.ok();
    }

    /**
     * Excel导入账号（使用EasyExcel）
     *
     * @param file          Excel文件
     * @param updateSupport 是否支持更新（账号已存在时更新会员时间）
     */
    @Operation(summary = "Excel导入账号", description = "Excel批量导入账号，updateSupport=true时更新已存在账号")
    @SaCheckPermission("mall:gameAccount:add")
    @Log(title = "游戏账号管理", businessType = BusinessType.IMPORT)
    @PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AccountImportResultDTO> importData(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean updateSupport) throws IOException {

        // 使用EasyExcel解析
        AccountImportListener listener = new AccountImportListener();
        val accountImportDTOExcelResult = ExcelUtil.importExcel(file.getInputStream(), AccountImportDTO.class, listener);

        // 获取解析后的数据
        List<AccountImportDTO> list = listener.getList();

        // 执行业务导入
        AccountImportResultDTO result = accountImportService.importByExcel(list, updateSupport);

        // 合并Excel解析错误
        if (!listener.getErrorList().isEmpty()) {
            List<RowErrorDTO> allErrors = new ArrayList<>(result.getErrors());
            allErrors.addAll(listener.getErrorList());
            result.setErrors(allErrors);
            result.setFailCount(allErrors.size());
        }

        // 返回导入结果
        if (result.getFailCount() > 0) {
            return R.fail("导入完成，新增" + result.getSuccessCount()
                    + "条，更新" + result.getUpdateCount()
                    + "条，失败" + result.getFailCount() + "条", result);
        }

        return R.ok(result);
    }

    private AccountImportDTO createTemplateRow(String account, Integer gameType, Integer days) {
        AccountImportDTO dto = new AccountImportDTO();
        dto.setUserAccount(account);
        dto.setGameType(gameType);
        dto.setDays(days);
        return dto;
    }
}
