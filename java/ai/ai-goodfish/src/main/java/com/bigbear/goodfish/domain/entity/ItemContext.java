package com.bigbear.goodfish.domain.entity;

import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;


@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Table("item_context")
public class ItemContext {
    @Id
    private String itemId;
    private String userId; // 添加用户ID字段
    private String itemInfo;
}
