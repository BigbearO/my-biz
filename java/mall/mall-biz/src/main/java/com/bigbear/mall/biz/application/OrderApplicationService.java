package com.bigbear.mall.biz.application;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.bigbear.mall.api.common.enums.GamesEnum;
import com.bigbear.mall.biz.domain.dto.OrderExcelDTO;
import com.bigbear.mall.biz.domain.dto.OrderImportDTO;
import com.bigbear.mall.biz.domain.dto.OrderImportResultDTO;
import com.bigbear.mall.biz.domain.dto.RowErrorDTO;
import com.bigbear.mall.biz.domain.entity.AccountEntity;
import com.bigbear.mall.biz.domain.entity.OrderEntity;
import com.bigbear.mall.biz.manager.IAccountManager;
import com.bigbear.mall.biz.manager.IOrderManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 订单应用服务
 * 负责复杂业务逻辑编排
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final IOrderManager orderManager;
    private final IAccountManager accountManager;

    /**
     * Excel导入订单（旧方法，保留兼容）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> importByExcel(List<OrderExcelDTO> orderList, BindingResult bindingResult) {
        Set<String> errorMessageList = new HashSet<>();

        for (OrderExcelDTO excel : orderList) {
            Set<String> errorMsg = new HashSet<>();
            // 检验订单是否已经导入
            boolean existOrder = orderManager.getByOrderNo(excel.getOrderNo()) != null;

            if (existOrder) {
                errorMsg.add("订单已经导入: " + excel.getOrderNo());
            }

            // 数据合法情况
            if (CollUtil.isEmpty(errorMsg)) {
                OrderEntity orderEntity = BeanUtil.copyProperties(excel, OrderEntity.class);
                orderManager.saveOrderInfo(orderEntity);
            } else {
                // 数据不合法情况
                errorMessageList.addAll(errorMsg);
            }
        }
        return errorMessageList.stream().toList();
    }

    /**
     * 导入单条订单并处理账号逻辑（事务方法）
     * 【改造】从批量处理改为单条处理，供Listener逐行调用
     *
     * @param dto 单条订单导入数据
     * @throws ServiceException 业务异常（校验失败、订单已存在等）
     */
    @Transactional(rollbackFor = Exception.class)
    public void importOrdersWithAccount(OrderImportDTO dto) {
        // 1. 行级校验
        List<String> errors = validateImportRow(dto);
        if (!errors.isEmpty()) {
            throw new ServiceException(String.join("; ", errors));
        }

        // 2. 执行导入（复用改造后的单条处理方法）
        importSingleOrderWithAccount(dto);
    }

    /**
     * 行级校验
     */
    private List<String> validateImportRow(OrderImportDTO dto) {
        List<String> errors = new ArrayList<>();

        // 1. 订单编号非空校验（Excel中必须提供）
        if (StrUtil.isBlank(dto.getOrderNo())) {
            errors.add("订单编号不能为空");
        }

        // 2. 账号非空
        if (StrUtil.isBlank(dto.getAccount())) {
            errors.add("账号不能为空");
        }

        // 3. 游戏类型合法性
        if (dto.getGameType() == null || !isValidGameType(dto.getGameType())) {
            errors.add("游戏类型不合法[" + dto.getGameType() + "],可选值:0,1");
        }

        // 4. 会员天数
        if (dto.getDays() == null || dto.getDays() <= 0) {
            errors.add("会员天数必须大于0");
        }

        return errors;
    }

    /**
     * 校验游戏类型是否合法
     */
    private boolean isValidGameType(Integer gameType) {
        for (GamesEnum e : GamesEnum.values()) {
            if (Integer.valueOf(e.getValue()).equals(gameType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 导入单条订单并处理账号（事务方法）
     * 【改造点】增加订单号校验：非空判断 + 存在性校验
     */
    @Transactional(rollbackFor = Exception.class)
    public void importSingleOrderWithAccount(OrderImportDTO dto) {
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime newExpireTime;

        // 【改造点】0. 校验订单号
        // 0.1 判断orderNo是否为空
        if (StrUtil.isBlank(dto.getOrderNo())) {
            throw new ServiceException("订单编号不能为空");
        }

        // 0.2 判断订单是否已存在
        OrderEntity existOrder = orderManager.getByOrderNo(dto.getOrderNo());
        if (existOrder != null) {
            throw new ServiceException("订单已经处理: " + dto.getOrderNo());
        }

        // 1. 查询或创建账号
        AccountEntity account = accountManager.lambdaQuery()
                .eq(AccountEntity::getUserAccount, dto.getAccount())
                .one();

        if (account == null) {
            // 新增账号
            account = new AccountEntity();
            account.setUserAccount(dto.getAccount());
            account.setType(dto.getGameType());
            account.setStartTime(today);
            newExpireTime = today.plusDays(dto.getDays());
            account.setExpireTime(newExpireTime);
            account.setEnabled(false);
            accountManager.save(account);
            log.debug("新增账号，account={}", dto.getAccount());
        } else {
            // 更新账号
            account.setType(dto.getGameType());

            if (!account.getExpireTime().isBefore(today)) {
                // expireTime >= 今天，仍在有效期内 - 只更新expireTime，startTime保持不变
                newExpireTime = today.plusDays(dto.getDays());
            } else {
                // 已过期 - 累加时间，startTime重置为今天
                account.setStartTime(today);
                newExpireTime = account.getExpireTime().plusDays(dto.getDays());
            }
            account.setExpireTime(newExpireTime);
            accountManager.updateById(account);
            log.debug("更新账号，account={}, expireTime={}", dto.getAccount(), newExpireTime);
        }

        // 2. 创建订单（使用Excel中的orderNo，不再预生成）
        OrderEntity order = new OrderEntity();
        order.setOrderNo(dto.getOrderNo());
        order.setUserId(dto.getAccount());
        order.setGameType(dto.getGameType());
        order.setDays(dto.getDays());
        order.setStatus(1); // 已支付
        order.setPayTime(today);
        orderManager.save(order);

        log.debug("创建订单成功，orderNo={}", dto.getOrderNo());
    }

    /**
     * 添加订单并处理账号逻辑（单笔）
     * 逻辑：
     * 1. 先根据orderNo查询是否存在对应订单，存在就报错订单已经处理
     * 2. 账号不存在：新增账号，startTime=今天，expireTime=今天+days
     * 3. 账号存在且expireTime>今天：startTime不变，expireTime=今天+days
     * 4. 账号存在且expireTime<=今天：startTime=今天，expireTime=原expireTime+days（累加）
     *
     * @param order 订单实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void addOrderWithAccount(OrderEntity order) {
        if (StrUtil.isBlank(order.getOrderNo())) {
            throw new RuntimeException("订单编号不存在");
        }
        // 0. 检查订单是否已存在（如果传入了orderNo）

            OrderEntity existOrder = orderManager.getByOrderNo(order.getOrderNo());
            if (existOrder != null) {
                throw new RuntimeException("订单已经处理: " + order.getOrderNo());
            }


        LocalDateTime today = LocalDateTime.now();
        LocalDateTime newExpireTime;

        // 1. 查询账号
        AccountEntity account = accountManager.lambdaQuery()
                .eq(AccountEntity::getUserAccount, order.getUserId())
                .one();

        if (account == null) {
            // 账号不存在 - 新增账号
            account = new AccountEntity();
            account.setUserAccount(order.getUserId());
            account.setType(order.getGameType());
            account.setStartTime(today);
            newExpireTime = today.plusDays(order.getDays());
            account.setExpireTime(newExpireTime);
            account.setEnabled(false);
            accountManager.save(account);
            log.info("新增账号，account={}", order.getUserId());
        } else {
            // 账号存在 - 更新账号
            account.setType(order.getGameType());

            if (account.getExpireTime().isBefore(today)) {
                // expireTime < 今天，不在在有效期内 - startTime重置为今天
                account.setStartTime(today);
                newExpireTime = today.plusDays(order.getDays());
            } else {
                // expireTime >= 今天，已过期 - 累加时间， startTime保持不变，
                newExpireTime = account.getExpireTime().plusDays(order.getDays());
            }
            account.setExpireTime(newExpireTime);
            accountManager.updateById(account);
            log.debug("更新账号，account={}, expireTime={}", order.getUserId(), newExpireTime);
        }

        // 2. 保存订单
        order.setStatus(1); // 已支付
        order.setPayTime(today);
        orderManager.save(order);

        log.debug("创建订单成功，orderNo={}", order.getOrderNo());
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "ORDER" + System.currentTimeMillis() + (int) (Math.random() * 1000);
    }
}
