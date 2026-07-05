package com.bigbear.goodfish.domain.bo;

import com.bigbear.goodfish.domain.entity.AccountEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import jakarta.validation.constraints.*;

/**
 * 账号业务对象 account
 *
 * @author LionLi
 * @date 2025-11-09
 */
@Data
//@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AccountEntity.class, reverseConvertGenerate = false)
public class AccountBo  {

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
