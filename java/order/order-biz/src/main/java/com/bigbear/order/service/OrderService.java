package com.bigbear.order.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bigbear.order.infrastructure.AccountManager;
import com.bigbear.order.infrastructure.GameTaskSender;
import com.bigbear.order.infrastructure.OrderManager;
import com.bigbear.order.infrastructure.entity.dto.OrderImportDTO;
import com.bigbear.order.infrastructure.entity.po.AccountPO;
import com.bigbear.order.infrastructure.entity.po.OrderPO;
import com.pig4cloud.pig.common.core.util.MsgUtils;
import com.pig4cloud.plugin.excel.vo.ErrorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final AccountManager accountManager;
    private final OrderManager orderManager;

    private static volatile Map<Integer, GameTaskSender> gameTaskSenderServiceMap;


    public void task() {
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<AccountPO> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.ge(AccountPO::getStartTime, now);
        queryWrapper.le(AccountPO::getExpireTime, now);

        int pageNo = 1;
        int pageSize = 100;
        Page<AccountPO> res = null;
        do {
            res = accountManager.page(new Page<>(pageNo, pageSize), queryWrapper);
            if (CollectionUtils.isEmpty(res.getRecords())) {
                log.info("no data");
                break;
            }
            ++pageNo;
            doTask(res.getRecords());
        } while (res.hasNext());

    }

    void doTask(List<AccountPO> dataList) {

//        根据不同type,发送到不同队列里面
        for (AccountPO accountPO : dataList) {
            try {
                GameTaskSender gameTaskSender = getGameTaskSender(accountPO.getType());
                gameTaskSender.send2Auto(accountPO,accountPO.getType());
            } catch (Exception e) {
                log.warn("自动任务发送失败,账号{}，游戏{}",accountPO.getUserAccount(),accountPO.getType());
            }

        }

    }

    private GameTaskSender getGameTaskSender(Integer type) {

        if (gameTaskSenderServiceMap == null) {
            synchronized (GameTaskSender.class) {
                if (gameTaskSenderServiceMap == null) {
                    Collection<GameTaskSender> beans = SpringUtil.getBeansOfType(GameTaskSender.class).values();
                    gameTaskSenderServiceMap = beans.stream().collect(Collectors.toMap(GameTaskSender::type, gameTaskSender -> gameTaskSender, (v1, v2) -> v2));
                }
            }
        }

        return gameTaskSenderServiceMap.get(type);
    }


    public  List<ErrorMessage> importByExcel(List<OrderImportDTO> orderList, BindingResult bindingResult) {

        // 通用校验获取失败的数据
        List<ErrorMessage> errorMessageList = (List<ErrorMessage>) bindingResult.getTarget();

        // 执行数据插入操作 组装 RoleDto
        for (OrderImportDTO excel : orderList) {
            Set<String> errorMsg = new HashSet<>();
            // 检验订单是否已经导入
            boolean existOrder = orderManager.getByOrderNo(excel.getOrderNo())==null;

            if (existOrder) {
                errorMsg.add(MsgUtils.getMessage("订单已经导入", excel.getOrderNo(),
                        excel.getOrderNo()));
            }

            // 数据合法情况
            if (CollUtil.isEmpty(errorMsg)) {
                OrderPO orderPO = BeanUtil.copyProperties(excel, OrderPO.class);
                orderManager.save(orderPO);
            } else {
                // 数据不合法情况
                errorMessageList.add(new ErrorMessage(excel.getLineNum(), errorMsg));
            }
        }
        return errorMessageList;
    }

}
