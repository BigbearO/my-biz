package com.bigbear.goodfish2.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.bigbear.goodfish2.domain.entity.ItemEntity;
import io.github.linpeilie.annotations.AutoMapper;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;



/**
 * 商品视图对象 item
 *
 * @author LionLi
 * @date 2025-10-27
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ItemEntity.class)
public class ItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * $column.columnComment
     */
    @ExcelProperty(value = "${comment}", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "$column.readConverterExp()")
    private Long id;

    /**
     * 关联的闲鱼平台商品id
     */
    @ExcelProperty(value = "关联的闲鱼平台商品id")
    private String itemId;

    /**
     * 商品数据，JSON格式存储
     */
    @ExcelProperty(value = "商品数据，JSON格式存储")
    private String data;

    /**
     * 商品价格
     */
    @ExcelProperty(value = "商品价格")
    private Long price;

    /**
     * 商品描述
     */
    @ExcelProperty(value = "商品描述")
    private String description;

    /**
     * 商品标题
     */
    @ExcelProperty(value = "商品标题")
    private String title;

    /**
     * 商品分类
     */
    @ExcelProperty(value = "商品分类")
    private String category;

    /**
     * 商品所在地
     */
    @ExcelProperty(value = "商品所在地")
    private String location;

    /**
     * 商品图片URL
     */
    @ExcelProperty(value = "商品图片URL")
    private String imageUrl;

    /**
     * 卖家ID
     */
    @ExcelProperty(value = "卖家ID")
    private String sellerId;

    /**
     * 商品状态
     */
    @ExcelProperty(value = "商品状态")
    private String condition;

    /**
     * 发布日期时间戳
     */
    @ExcelProperty(value = "发布日期时间戳")
    private Long publishedDate;

    /**
     * 库存状态
     */
    @ExcelProperty(value = "库存状态")
    private String stockStatus;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createdAt;

    /**
     * 更新时间
     */
    @ExcelProperty(value = "更新时间")
    private Date updatedAt;


}
