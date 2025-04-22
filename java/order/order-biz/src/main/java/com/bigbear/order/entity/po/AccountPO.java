package com.bigbear.order.entity.po;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
//import com.pig4cloud.common.core.util.TenantTable;
import java.time.LocalDateTime;

/**
 * 游戏账号表
 *
 * @author bigbear
 * @date 2025-04-22 21:33:23
 */
@Data
//@TenantTable
@TableName("account")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "游戏账号表")
public class AccountPO extends Model<AccountPO> {


	/**
	* 订单表主键
	*/
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description="订单表主键")
    private Long id;

	/**
	* 用户账号
	*/
    @Schema(description="用户账号")
    private String userAccount;

	/**
	* 用户登录凭证
	*/
    @Schema(description="用户登录凭证")
    private String credential;

	/**
	* 开始时间
	*/
    @Schema(description="开始时间")
    private LocalDateTime startTime;

	/**
	* 过期时间
	*/
    @Schema(description="过期时间")
    private LocalDateTime expireTime;

	/**
	* 游戏类型
	*/
    @Schema(description="游戏类型")
    private Integer type;

	/**
	* 创建人
	*/
	@TableField(fill = FieldFill.INSERT)
    @Schema(description="创建人")
    private String createBy;

	/**
	* 创建时间
	*/
	@TableField(fill = FieldFill.INSERT)
    @Schema(description="创建时间")
    private LocalDateTime createTime;

	/**
	* 修改人
	*/
	@TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description="修改人")
    private String updateBy;

	/**
	* 更新时间
	*/
	@TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description="更新时间")
    private LocalDateTime updateTime;

	/**
	* 多租户id
	*/
    @Schema(description="多租户id")
    private Long tenantId;
}
