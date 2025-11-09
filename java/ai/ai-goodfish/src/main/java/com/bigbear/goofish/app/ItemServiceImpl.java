package com.bigbear.goofish.app;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bigbear.ai.biz.domain.bo.ItemBo;
import com.bigbear.ai.biz.domain.entity.ItemEntity;
import com.bigbear.ai.biz.domain.entity.ItemInfo;
import com.bigbear.ai.biz.domain.entity.ItemVectorEntity;
import com.bigbear.ai.biz.domain.vo.ItemVo;
import com.bigbear.ai.biz.manager.ItemManager;
import com.bigbear.ai.biz.mapper.ItemMapper;
import com.bigbear.ai.biz.mapper.ItemVectorMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;

/**
 * 商品Service业务层处理
 *
 * @author LionLi
 * @date 2025-10-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements IItemService {

    private final ItemManager itemManager;

    private final ItemMapper baseMapper;

    private final ItemVectorMapper itemVectorMapper;

    private final EmbeddingModel embeddingModel;
    /**
     * 查询商品
     *
     * @param id 主键
     * @return 商品
     */
    @Override
    public ItemVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询商品列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 商品分页列表
     */
    @Override
    public TableDataInfo<ItemVo> queryPageList(ItemBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ItemEntity> lqw = buildQueryWrapper(bo);
        Page<ItemVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的商品列表
     *
     * @param bo 查询条件
     * @return 商品列表
     */
    @Override
    public List<ItemVo> queryList(ItemBo bo) {
        LambdaQueryWrapper<ItemEntity> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<ItemEntity> buildQueryWrapper(ItemBo bo) {
        LambdaQueryWrapper<ItemEntity> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(ItemEntity::getId);
        lqw.eq(StringUtils.isNotBlank(bo.getItemId()), ItemEntity::getItemId, bo.getItemId());
        lqw.eq(StringUtils.isNotBlank(bo.getData()), ItemEntity::getData, bo.getData());
        lqw.eq(bo.getPrice() != null, ItemEntity::getPrice, bo.getPrice());
        lqw.eq(StringUtils.isNotBlank(bo.getDescription()), ItemEntity::getDescription, bo.getDescription());
        lqw.eq(StringUtils.isNotBlank(bo.getTitle()), ItemEntity::getTitle, bo.getTitle());
        lqw.eq(StringUtils.isNotBlank(bo.getCategory()), ItemEntity::getCategory, bo.getCategory());
        lqw.eq(StringUtils.isNotBlank(bo.getLocation()), ItemEntity::getLocation, bo.getLocation());
        lqw.eq(StringUtils.isNotBlank(bo.getImageUrl()), ItemEntity::getImageUrl, bo.getImageUrl());
        lqw.eq(StringUtils.isNotBlank(bo.getSellerId()), ItemEntity::getSellerId, bo.getSellerId());
        lqw.eq(StringUtils.isNotBlank(bo.getCondition()), ItemEntity::getCondition, bo.getCondition());
        lqw.eq(bo.getPublishedDate() != null, ItemEntity::getPublishedDate, bo.getPublishedDate());
        lqw.eq(StringUtils.isNotBlank(bo.getStockStatus()), ItemEntity::getStockStatus, bo.getStockStatus());
        lqw.eq(bo.getCreatedAt() != null, ItemEntity::getCreatedTime, bo.getCreatedAt());
        lqw.eq(bo.getUpdatedAt() != null, ItemEntity::getUpdatedTime, bo.getUpdatedAt());
        return lqw;
    }

    /**
     * 新增商品
     *
     * @param bo 商品
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(ItemBo bo) {
        ItemEntity add = MapstructUtils.convert(bo, ItemEntity.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改商品
     *
     * @param bo 商品
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(ItemBo bo) {
        ItemEntity update = MapstructUtils.convert(bo, ItemEntity.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(ItemEntity entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除商品信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }


    /**
     * 向量化商品信息
     */
    public void vectorizeItem(ItemInfo itemInfo) {
        try {
            // 构建商品文本内容
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("商品描述: ").append(itemInfo.getItemDO().getDesc()).append("\n");
            contentBuilder.append("售价: ").append(itemInfo.getItemDO().getSoldPrice()).append("\n");
            if (itemInfo.getItemDO().getPricingStrategyPrompt() != null) {
                contentBuilder.append("定价策略: ").append(itemInfo.getItemDO().getPricingStrategyPrompt()).append("\n");
            }

            // 添加标签信息
            if (itemInfo.getItemDO().getItemLabelExtList() != null) {
                contentBuilder.append("商品标签: ");
                for (ItemInfo.ItemLabelExt labelExt : itemInfo.getItemDO().getItemLabelExtList()) {
                    contentBuilder.append(labelExt.getProperties()).append(", ");
                }
                contentBuilder.append("\n");
            }

            // 添加卖家信息
            if (itemInfo.getSellerDO() != null) {
                contentBuilder.append("卖家城市: ").append(itemInfo.getSellerDO().getCity()).append("\n");
                contentBuilder.append("卖家签名: ").append(itemInfo.getSellerDO().getSignature()).append("\n");
            }

            String itemContent = contentBuilder.toString();

            // 生成向量
            Embedding embedding = embeddingModel.embed(itemContent).content();

            // 保存向量到数据库
            Map<String, Float> vectorMap = new HashMap<>();
            for (int i = 0; i < embedding.dimension(); i++) {
                vectorMap.put(String.valueOf(i), embedding.vector()[i]);
            }

            String vectorJson = JSONUtil.toJsonStr(vectorMap);

            // 检查是否已存在
            ItemVectorEntity existing = itemVectorMapper.selectByItemIdAndTenantId(
                String.valueOf(itemInfo.getId()), itemInfo.getTenantId());

            if (existing != null) {
                // 更新
                LambdaUpdateWrapper<ItemVectorEntity> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(ItemVectorEntity::getItemId, String.valueOf(itemInfo.getId()))
                    .eq(ItemVectorEntity::getTenantId, itemInfo.getTenantId())
                    .set(ItemVectorEntity::getVectorData, vectorJson)
                    .set(ItemVectorEntity::getItemContent, itemContent)
                    .set(ItemVectorEntity::getUpdateTime, LocalDateTime.now());
                itemVectorMapper.update(null, updateWrapper);
            } else {
                // 新增
                ItemVectorEntity entity = new ItemVectorEntity();
                entity.setTenantId(itemInfo.getTenantId());
                entity.setItemId(String.valueOf(itemInfo.getId()));
                entity.setVectorData(vectorJson);
                entity.setItemContent(itemContent);
                entity.setCreateTime(LocalDateTime.now());
                entity.setUpdateTime(LocalDateTime.now());
                itemVectorMapper.insert(entity);
            }

            log.info("商品向量化完成: tenantId={}, itemId={}", itemInfo.getTenantId(), itemInfo.getId());

        } catch (Exception e) {
            log.error("商品向量化失败: {}", e.getMessage(), e);
        }
    }


    /**
     * 获取商品向量
     */
    public ItemVectorEntity getItemVector(String tenantId, String itemId) {
        return itemVectorMapper.selectByItemIdAndTenantId(itemId, tenantId);
    }


}
