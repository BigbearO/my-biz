package org.dromara.goodfish.meta.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.goodfish.meta.domain.GoodfishAccount;

import java.io.Serial;
import java.io.Serializable;

/**
 * 账号管理业务对象 goodfish_account（后台查询 + 备注修改）.
 *
 * @author goodfish
 */
@Data
@AutoMapper(target = GoodfishAccount.class, reverseConvertGenerate = false)
public class GoodfishAccountBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（修改备注时必填）
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 闲鱼用户 ID（列表查询条件，模糊匹配）
     */
    private String goodfishId;

    /**
     * 闲鱼昵称（列表查询条件，模糊匹配）
     */
    private String goodfishNickname;

    /**
     * 账号状态（列表查询条件：normal / disabled）
     */
    private String status;

    /**
     * 备注（修改时携带）
     */
    private String remark;

}
