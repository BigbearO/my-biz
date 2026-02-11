package com.bigbear.mall.biz.listener;

import cn.hutool.core.util.StrUtil;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import com.bigbear.mall.biz.domain.dto.AccountImportDTO;
import com.bigbear.mall.biz.domain.dto.OrderImportDTO;
import com.bigbear.mall.biz.domain.dto.RowErrorDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.excel.core.ExcelListener;
import org.dromara.common.excel.core.ExcelResult;

/**
 * EasyExcel账号导入监听器
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
public class AccountImportListener extends AnalysisEventListener<AccountImportDTO>  implements ExcelListener<AccountImportDTO>  {

    private final List<AccountImportDTO> list = new ArrayList<>();
    private final List<RowErrorDTO> errorList = new ArrayList<>();

    @Override
    public void invoke(AccountImportDTO dto, AnalysisContext context) {
        // 基础格式校验
        int rowNum = context.readRowHolder().getRowIndex() + 1;

        if (StrUtil.isBlank(dto.getUserAccount())) {
            errorList.add(new RowErrorDTO(rowNum, null, "账号不能为空"));
            return;
        }

        list.add(dto);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("Excel解析完成，共{}条数据", list.size());
    }

    public List<AccountImportDTO> getList() {
        return list;
    }

    public List<RowErrorDTO> getErrorList() {
        return errorList;
    }

    @Override
    public ExcelResult<AccountImportDTO> getExcelResult()  {
        return null;
    }
}
