package org.dromara.goodfish.meta.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.goodfish.meta.domain.GoodfishAccount;

import java.io.Serial;
import java.io.Serializable;

/**
 * 账号上报业务对象（客户端「添加 cookie 验证通过」后上报）.
 *
 * <p>★不接收 Cookie（A10）：仅 goodfish_id / goodfish_nickname / remark.</p>
 *
 * @author goodfish
 */
@Data
@AutoMapper(target = GoodfishAccount.class, reverseConvertGenerate = false)
public class GoodfishAccountReportBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 闲鱼用户 ID
     */
    @NotBlank(message = "闲鱼用户ID不能为空", groups = {AddGroup.class})
    private String goodfishId;

    /**
     * 闲鱼昵称
     */
    @NotBlank(message = "闲鱼昵称不能为空", groups = {AddGroup.class})
    private String goodfishNickname;

    /**
     * 备注
     */
    private String remark;

}
