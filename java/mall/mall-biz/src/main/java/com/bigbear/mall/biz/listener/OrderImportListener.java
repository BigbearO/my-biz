package com.bigbear.mall.biz.listener;

import cn.hutool.core.util.StrUtil;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import com.bigbear.mall.biz.domain.dto.AccountImportDTO;
import com.bigbear.mall.biz.domain.dto.OrderImportDTO;
import com.bigbear.mall.biz.domain.dto.RowErrorDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.excel.core.ExcelListener;
import org.dromara.common.excel.core.ExcelResult;

/**
 * 订单导入EasyExcel监听器
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Getter
@Slf4j
public class OrderImportListener extends AnalysisEventListener<OrderImportDTO>  implements ExcelListener<OrderImportDTO> {

    private final List<OrderImportDTO> list = new ArrayList<>();
    private final List<RowErrorDTO> errorList = new ArrayList<>();
    private int successNum = 0;
    private int failureNum = 0;
    private final StringBuilder successMsg = new StringBuilder();
    private final StringBuilder failureMsg = new StringBuilder();

    @Override
    public void invoke(OrderImportDTO dto, AnalysisContext context) {
        int rowNum = context.readRowHolder().getRowIndex() + 1;

        // 基础校验
        if (StrUtil.isBlank(dto.getAccount())) {
            errorList.add(new RowErrorDTO(rowNum, null, "账号不能为空"));
            return;
        }

        list.add(dto);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("订单Excel解析完成，共{}条数据", list.size());
    }

    @Override
    public ExcelResult<OrderImportDTO> getExcelResult() {
        return new ExcelResult<>() {

            @Override
            public String getAnalysis() {
                if (failureNum > 0) {
                    failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
                    throw new ServiceException(failureMsg.toString());
                } else {
                    successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
                }
                return successMsg.toString();
            }

            @Override
            public List<OrderImportDTO> getList() {
                return null;
            }

            @Override
            public List<String> getErrorList() {
                return null;
            }
        };
    }
}
