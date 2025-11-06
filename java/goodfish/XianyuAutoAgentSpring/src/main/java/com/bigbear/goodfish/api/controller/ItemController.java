package com.bigbear.goodfish.api.controller;

import com.bigbear.goodfish.domain.entity.Item;
import com.bigbear.goodfish.domain.service.ItemService;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商品控制器
 *
 * @author autoagent
 */
@Slf4j
@RestController
@RequestMapping("/api/item")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * 获取商品信息
     */
    @GetMapping("/{itemId}")
    public Map<String, Object> getItemInfo(@PathVariable String itemId) {
        try {
            Item item = itemService.getItemById(itemId);
            if (item == null) {
                return Map.of("success", false, "message", "商品不存在");
            }
            var data = new HashMap<String, Object>();

            data.put("itemId", item.getItemId());
            data.put("data", item.getData());
            data.put("price", item.getPrice());
            data.put("title", item.getTitle());

            data.put("description", item.getDescription());

                data.put("category", item.getCategory());
            data.put("location", item.getLocation());
            data.put("imageUrl", item.getImageUrl());
            data.put("sellerId", item.getSellerId());
            data.put("condition", item.getCondition());
            data.put("publishedDate", item.getPublishedDate());
            data.put("stockStatus", item.getStockStatus());
            data.put("lastUpdated", item.getUpdatedAt());
//            data.put();

            return Map.of(
                "success", true,
                "message", "获取商品成功",
                "data",
                data

            );
        } catch (Exception e) {
            log.error("获取商品失败", e);
            return Map.of("success", false, "message", "获取商品失败: " + e.getMessage());
        }
    }

    /**
     * 保存或更新商品信息
     */
    @PostMapping("/save")
    public Map<String, Object> saveOrUpdateItem(@RequestBody Map<String, Object> request) {
        try {
            String itemId = (String) request.get("itemId");
            if (itemId == null || itemId.isEmpty()) {
                return Map.of("success", false, "message", "缺少商品ID");
            }

            Item item = new Item();
            item.setItemId(itemId);

            // 设置商品信息
            if (request.containsKey("data")) {
                item.setData((String) request.get("data"));
            }
            if (request.containsKey("price")) {
                item.setPrice((Double) request.get("price"));
            }
            if (request.containsKey("title")) {
                item.setTitle((String) request.get("title"));
            }
            if (request.containsKey("description")) {
                item.setDescription((String) request.get("description"));
            }
            if (request.containsKey("category")) {
                item.setCategory((String) request.get("category"));
            }
            if (request.containsKey("location")) {
                item.setLocation((String) request.get("location"));
            }
            if (request.containsKey("imageUrl")) {
                item.setImageUrl((String) request.get("imageUrl"));
            }
            if (request.containsKey("sellerId")) {
                item.setSellerId((String) request.get("sellerId"));
            }
            if (request.containsKey("condition")) {
                item.setCondition((String) request.get("condition"));
            }
            if (request.containsKey("publishedDate")) {
                item.setPublishedDate((Long) request.get("publishedDate"));
            }
            if (request.containsKey("stockStatus")) {
                item.setStockStatus((String) request.get("stockStatus"));
            }

            // 保存或更新商品
            itemService.saveOrUpdateItem(
                item.getItemId(),
                item.getData(),
                item.getPrice(),
                item.getDescription()
                );

            return Map.of(
                    "success", true,
                    "message", "保存商品成功",
                    "data", Map.of("itemId", item.getItemId())
            );
        } catch (Exception e) {
            log.error("保存商品失败", e);
            return Map.of("success", false, "message", "保存商品失败: " + e.getMessage());
        }
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/{itemId}")
    public Map<String, Object> deleteItem(@PathVariable String itemId) {
        try {
            itemService.deleteItem(itemId);
            return Map.of(
                    "success", true,
                    "message", "删除商品成功"
            );
        } catch (Exception e) {
            log.error("删除商品失败", e);
            return Map.of(
                    "success", false,
                    "message", "删除商品失败: " + e.getMessage()
            );
        }
    }
}
