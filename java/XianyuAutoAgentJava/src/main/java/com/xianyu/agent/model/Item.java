package com.xianyu.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("items")
public class Item {
    @TableId(type = IdType.INPUT)
    private String itemId;
    private String data;
    private Double price;
    private String description;
    private LocalDateTime lastUpdated;
}
