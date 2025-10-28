package com.bigbear.aigc.biz.domain.bo;


import com.bigbear.aigc.biz.domain.entity.ItemEntity;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 商品业务对象 item
 *
 * @author LionLi
 * @date 2025-10-27
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ItemEntity.class, reverseConvertGenerate = false)
public class ItemBo extends BaseEntity {

    /**
     * $column.columnComment
     */
    @NotNull(message = "$column.columnComment不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 关联的闲鱼平台商品id
     */
    @NotBlank(message = "关联的闲鱼平台商品id不能为空", groups = { AddGroup.class, EditGroup.class })
    private String itemId;

    /**
     * 商品数据，JSON格式存储
     */
    private String data;

    /**
     * 商品价格
     */
    private Long price;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 商品标题
     */
    private String title;

    /**
     * 商品分类
     */
    private String category;

    /**
     * 商品所在地
     */
    private String location;

    /**
     * 商品图片URL
     */
    private String imageUrl;

    /**
     * 卖家ID
     */
    private String sellerId;

    /**
     * 商品状态
     */
    private String condition;

    /**
     * 发布日期时间戳
     */
    private Long publishedDate;

    /**
     * 库存状态
     */
    private String stockStatus;

    /**
     * 创建时间
     */
    @NotNull(message = "创建时间不能为空", groups = { AddGroup.class, EditGroup.class })
    private Date createdAt;

    /**
     * 更新时间
     */
    @NotNull(message = "更新时间不能为空", groups = { AddGroup.class, EditGroup.class })
    private Date updatedAt;


}
