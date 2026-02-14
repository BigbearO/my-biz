package com.bigbear.mall.biz.listener;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import com.bigbear.mall.biz.application.OrderApplicationService;
import com.bigbear.mall.biz.domain.dto.AccountImportDTO;
import com.bigbear.mall.biz.domain.dto.OrderImportDTO;
import com.bigbear.mall.biz.domain.dto.OrderImportResultDTO;
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


    private final List<RowErrorDTO> errorList = new ArrayList<>();
    private int successNum = 0;
    private int failureNum = 0;
    private final StringBuilder successMsg = new StringBuilder();
    private final StringBuilder failureMsg = new StringBuilder();
    private final OrderApplicationService  orderApplicationService;

    public OrderImportListener() {
        orderApplicationService = SpringUtil.getBean(OrderApplicationService.class);
    }

    @Override
    public void invoke(OrderImportDTO dto, AnalysisContext context) {
        int rowNum = context.readRowHolder().getRowIndex() + 1;

        try {
            // 调用服务层处理单条订单（带事务）
            orderApplicationService.importOrdersWithAccount(dto);

            // 【成功消息】不显示行号，格式：序号、订单账号 {account} 导入成功
            successNum++;
            successMsg.append("<br/>").append(successNum).append("、订单账号 ")
                    .append(dto.getAccount()).append(" 导入成功");

        } catch (ServiceException e) {
            // 【失败消息】显示行号，格式：序号、第{rowNum}行 订单账号 {account} {errorMsg}
            failureNum++;
            failureMsg.append("<br/>").append(failureNum).append("、第").append(rowNum)
                    .append("行 订单账号 ").append(dto.getAccount())
                    .append(" ").append(e.getMessage());

        } catch (Exception e) {
            // 【系统异常】同样显示行号
            failureNum++;
            failureMsg.append("<br/>").append(failureNum).append("、第").append(rowNum)
                    .append("行 订单账号 ").append(dto.getAccount())
                    .append(" 导入失败：").append(e.getMessage());
            log.error("订单导入失败，rowNum={}, account={}", rowNum, dto.getAccount(), e);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {

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
