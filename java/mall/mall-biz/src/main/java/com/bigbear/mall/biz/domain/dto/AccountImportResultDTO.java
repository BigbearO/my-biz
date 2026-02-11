package com.bigbear.mall.biz.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 账号导入结果DTO
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountImportResultDTO {

    /**
     * 新增成功数
     */
    private Integer successCount = 0;

    /**
     * 更新成功数
     */
    private Integer updateCount = 0;

    /**
     * 失败数
     */
    private Integer failCount = 0;

    /**
     * 错误列表
     */
    private List<RowErrorDTO> errors = new ArrayList<>();

    public void incrementSuccess() {
        this.successCount++;
    }

    public void incrementUpdate() {
        this.updateCount++;
    }

    public void addError(RowErrorDTO error) {
        this.errors.add(error);
        this.failCount++;
    }
}
