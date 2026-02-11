package com.bigbear.mall.biz.application.service;

import cn.hutool.core.util.StrUtil;
import com.bigbear.mall.api.common.enums.GamesEnum;
import com.bigbear.mall.biz.domain.dto.AccountImportDTO;
import com.bigbear.mall.biz.domain.dto.AccountImportResultDTO;
import com.bigbear.mall.biz.domain.dto.RowErrorDTO;
import com.bigbear.mall.biz.domain.entity.AccountEntity;
import com.bigbear.mall.biz.manager.IAccountManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 账号导入服务
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountImportService {

    private final IAccountManager accountManager;

    /**
     * Excel导入账号
     *
     * @param excelList     Excel解析后的数据列表
     * @param updateSupport 是否支持更新（账号已存在时是否更新会员时间）
     * @return 导入结果
     */
    public AccountImportResultDTO importByExcel(List<AccountImportDTO> excelList, boolean updateSupport) {
        AccountImportResultDTO result = new AccountImportResultDTO();

        for (int i = 0; i < excelList.size(); i++) {
            AccountImportDTO dto = excelList.get(i);
            int rowNum = i + 2; // Excel从第2行开始（第1行是表头）

            // 行级校验
            List<String> rowErrors = validateAccountRow(dto, updateSupport);
            if (!rowErrors.isEmpty()) {
                result.addError(new RowErrorDTO(rowNum, dto.getUserAccount(),
                        String.join("; ", rowErrors)));
                continue;
            }

            try {
                // 导入单个账号
                boolean isUpdate = importSingleAccount(dto, updateSupport);
                if (isUpdate) {
                    result.incrementUpdate();
                } else {
                    result.incrementSuccess();
                }
            } catch (Exception e) {
                log.error("导入账号失败, rowNum={}, account={}", rowNum, dto.getUserAccount(), e);
                result.addError(new RowErrorDTO(rowNum, dto.getUserAccount(),
                        "系统错误: " + e.getMessage()));
            }
        }

        return result;
    }

    /**
     * 行级校验
     */
    private List<String> validateAccountRow(AccountImportDTO dto, boolean updateSupport) {
        List<String> errors = new ArrayList<>();

        // 1. 账号非空
        if (StrUtil.isBlank(dto.getUserAccount())) {
            errors.add("账号不能为空");
            return errors;
        }

        // 2. 账号唯一性校验（如果不支持更新）
        if (!updateSupport) {
            AccountEntity existAccount = accountManager.lambdaQuery()
                    .eq(AccountEntity::getUserAccount, dto.getUserAccount())
                    .one();
            if (existAccount != null) {
                errors.add("账号[" + dto.getUserAccount() + "]已存在");
            }
        }

        // 3. 游戏类型合法性
        if (dto.getGameType() == null || !isValidGameType(dto.getGameType())) {
            errors.add("游戏类型不合法[" + dto.getGameType() + "]，可选值:0,1");
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
        if (gameType == null) {
            return false;
        }
        for (GamesEnum e : GamesEnum.values()) {
            if (Integer.valueOf(e.getValue()).equals(gameType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 导入单个账号
     *
     * @return true-更新, false-新增
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean importSingleAccount(AccountImportDTO dto, boolean updateSupport) {
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime expireTime = startTime.plusDays(dto.getDays());

        // 查询账号是否已存在
        AccountEntity existAccount = accountManager.lambdaQuery()
                .eq(AccountEntity::getUserAccount, dto.getUserAccount())
                .one();

        if (existAccount != null && updateSupport) {
            // 更新现有账号的会员时间
            accountManager.lambdaUpdate()
                    .set(AccountEntity::getStartTime, startTime)
                    .set(AccountEntity::getExpireTime, expireTime)
                    .set(AccountEntity::getType, dto.getGameType())
                    .eq(AccountEntity::getId, existAccount.getId())
                    .update();
            log.info("更新账号会员时间，account={}", dto.getUserAccount());
            return true;
        } else {
            // 新增账号
            AccountEntity account = new AccountEntity();
            account.setUserAccount(dto.getUserAccount().trim());
            account.setType(dto.getGameType());
            account.setStartTime(startTime);
            account.setExpireTime(expireTime);
            account.setEnabled(false); // 初始未启用
            account.setEmulatorId(null);

            accountManager.save(account);
            log.info("新增账号，account={}", dto.getUserAccount());
            return false;
        }
    }
}
