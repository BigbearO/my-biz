package com.bigbear.goofish.domain.bo;

import com.bigbear.goofish.domain.entity.AccountEntity;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

/**
 * 账号业务对象 account
 *
 * @author LionLi
 * @date 2025-11-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AccountEntity.class, reverseConvertGenerate = false)
public class AccountBo extends BaseEntity {

    /**
     * $column.columnComment
     */
    @NotNull(message = "$column.columnComment不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * $column.columnComment
     */
    @NotBlank(message = "$column.columnComment不能为空", groups = { AddGroup.class, EditGroup.class })
    private String userId;

    /**
     * $column.columnComment
     */
    @NotBlank(message = "$column.columnComment不能为空", groups = { AddGroup.class, EditGroup.class })
    private String deviceId;


}
