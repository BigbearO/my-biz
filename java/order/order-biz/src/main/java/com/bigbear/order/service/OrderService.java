package com.bigbear.order.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bigbear.order.infrastructure.GameTaskSender;
import com.bigbear.order.infrastructure.entity.dto.OrderExcelDTO;
import com.bigbear.order.infrastructure.entity.po.AccountPO;
import com.bigbear.order.infrastructure.entity.po.OrderPO;
import com.bigbear.order.infrastructure.repository.AccountManager;
import com.bigbear.order.infrastructure.repository.OrderManager;
import com.pig4cloud.pig.common.core.util.MsgUtils;
import com.pig4cloud.plugin.excel.vo.ErrorMessage;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {



    private final OrderManager orderManager;





    @Transactional(rollbackFor = Exception.class)
    public  List<ErrorMessage> importByExcel(List<OrderExcelDTO> orderList, BindingResult bindingResult) {

        // 通用校验获取失败的数据
        List<ErrorMessage> errorMessageList = (List<ErrorMessage>) bindingResult.getTarget();

        // 执行数据插入操作 组装 RoleDto
        for (OrderExcelDTO excel : orderList) {
            Set<String> errorMsg = new HashSet<>();
            // 检验订单是否已经导入
            boolean existOrder = orderManager.getByOrderNo(excel.getOrderNo())!=null;

            if (existOrder) {
                errorMsg.add(MsgUtils.getMessage("订单已经导入", excel.getOrderNo(),
                        excel.getOrderNo()));
            }

            // 数据合法情况
            if (CollUtil.isEmpty(errorMsg)) {
                // todo 保存发送mq去增加这个用户的总时长
                OrderPO orderPO = BeanUtil.copyProperties(excel, OrderPO.class);
                orderManager.saveOrderIno(orderPO);
            } else {
                // 数据不合法情况
                errorMessageList.add(new ErrorMessage(excel.getLineNum(), errorMsg));
            }
        }
        return errorMessageList;
    }

}
