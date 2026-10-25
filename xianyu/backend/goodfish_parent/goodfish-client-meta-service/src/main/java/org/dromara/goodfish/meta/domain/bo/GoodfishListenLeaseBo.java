package org.dromara.goodfish.meta.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 监听租约业务对象（申请 / 续期 / 释放）.
 *
 * @author goodfish
 */
@Data
public class GoodfishListenLeaseBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 客户端实例标识（机器码 + 本地服务启动 ID，首次启动生成并持久化）
     */
    @NotBlank(message = "客户端实例标识不能为空")
    private String clientInstanceId;

}
