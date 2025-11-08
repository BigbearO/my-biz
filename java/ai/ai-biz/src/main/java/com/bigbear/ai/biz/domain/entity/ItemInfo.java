package com.bigbear.ai.biz.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 商品信息实体类
 */
@Data
public class ItemInfo {
    private Long id;
    private String tenantId;
    private ItemDO itemDO;
    private SellerDO sellerDO;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemDO {
        private String soldPrice;
        private List<ItemLabelExt> itemLabelExtList;
        private String desc;
        private String pricingStrategyPrompt; // 定价策略提示词字段
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemLabelExt {
        private String properties; // ex. "properties" : "122216545##是否评级卡:21959##否"
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SellerDO {
        private String city;
        private String signature;
    }


}
