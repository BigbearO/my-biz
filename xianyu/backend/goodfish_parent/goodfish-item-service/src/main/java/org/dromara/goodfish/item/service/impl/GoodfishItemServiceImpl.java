package org.dromara.goodfish.item.service.impl;

import account.v1.Account;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;
import org.dromara.goodfish.item.domain.GoodfishItem;
import org.dromara.goodfish.item.domain.GoodfishItemPrompt;
import org.dromara.goodfish.item.domain.bo.GoodfishItemCopyPromptBo;
import org.dromara.goodfish.item.domain.bo.GoodfishItemEditBo;
import org.dromara.goodfish.item.domain.bo.GoodfishItemQueryBo;
import org.dromara.goodfish.item.domain.bo.GoodfishItemSyncBo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemSyncCountVo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemSyncVo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;
import org.dromara.goodfish.item.manager.AccountClientManager;
import org.dromara.goodfish.item.manager.GoodfishItemManager;
import org.dromara.goodfish.item.manager.GoodfishItemPromptManager;
import org.dromara.goodfish.item.manager.ItemRuntimeCacheManager;
import org.dromara.goodfish.item.service.IGoodfishItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 闲鱼商品Service业务层实现（业务编排，不直接操作数据库）.
 *
 * @author goodfish
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GoodfishItemServiceImpl implements IGoodfishItemService {

    private final GoodfishItemManager itemManager;
    private final GoodfishItemPromptManager promptManager;
    private final AccountClientManager accountClientManager;
    private final ItemRuntimeCacheManager runtimeCacheManager;

    /**
     * 商品同步：账号解析 → 停用校验 → 批量 upsert → 删运行时缓存.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoodfishItemSyncVo syncItems(GoodfishItemSyncBo bo) {
        // 1. gRPC 解析账号（先解析后校验，顺序不可颠倒）
        Account.AccountInfo account = accountClientManager.getByGoodfishId(bo.getGoodfishAccountId());
        if (account == null) {
            log.info("[{}] 同步中止：云端无此账号 goodfishId={}",
                GoodfishItemConstants.LOGGER_ITEM_SERVICE, bo.getGoodfishAccountId());
            throw new ServiceException(GoodfishItemConstants.MSG_ACCOUNT_NOT_FOUND,
                GoodfishItemConstants.CODE_ACCOUNT_NOT_FOUND);
        }
        // 2. 校验账号状态（停用 → 整批拒绝）
        if (GoodfishItemConstants.ACCOUNT_STATUS_DISABLED.equals(account.getStatus())) {
            log.info("[{}] 同步中止：账号已停用 goodfishId={}",
                GoodfishItemConstants.LOGGER_ITEM_SERVICE, bo.getGoodfishAccountId());
            throw new ServiceException(GoodfishItemConstants.MSG_ACCOUNT_DISABLED,
                GoodfishItemConstants.CODE_ACCOUNT_DISABLED);
        }
        // 3. 批量 upsert（单条 CTE 语句：保留提示词、软删记录复活）
        GoodfishItemSyncCountVo count =
            itemManager.upsertBatch(account.getId(), bo.getGoodfishAccountId(), bo.getItems());
        // 4. 变更后主动删除运行时缓存（TTL 兜底之外的即时失效）
        runtimeCacheManager.deleteAll(bo.getGoodfishAccountId(),
            bo.getItems().stream().map(item -> item.getGoodfishItemId()).toList());
        log.info("[{}] 商品同步完成 goodfishId={}, 新增={}, 更新={}",
            GoodfishItemConstants.LOGGER_ITEM_SERVICE, bo.getGoodfishAccountId(),
            count.getInsertedCount(), count.getUpdatedCount());
        GoodfishItemSyncVo vo = new GoodfishItemSyncVo();
        vo.setInserted(count.getInsertedCount());
        vo.setUpdated(count.getUpdatedCount());
        return vo;
    }

    /**
     * 分页查询商品列表.
     */
    @Override
    public PageResult<GoodfishItemVo> queryPageList(GoodfishItemQueryBo bo, PageQuery pageQuery) {
        IPage<GoodfishItemVo> page = itemManager.selectVoPage(pageQuery,
            bo.getGoodfishAccountId(), bo.getTitle());
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 查询商品详情（含提示词；提示词未配置时最大回复字数回落默认值）.
     */
    @Override
    public GoodfishItemVo queryById(Long id) {
        GoodfishItemVo vo = itemManager.requireVoWithPromptById(id);
        if (vo.getMaxReplyLength() == null) {
            vo.setMaxReplyLength(GoodfishItemConstants.DEFAULT_MAX_REPLY_LENGTH);
        }
        return vo;
    }

    /**
     * 更新商品 + 提示词（库存状态只读，不在此更新）.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean editItem(GoodfishItemEditBo bo) {
        GoodfishItemVo exist = itemManager.requireVoWithPromptById(bo.getId());
        itemManager.updateBasicById(bo.getId(), bo.getTitle(), bo.getDescription(), bo.getPrice());
        promptManager.saveByItemId(buildPrompt(bo.getId(), bo.getSystemPrompt(), bo.getPricePrompt(),
            bo.getTechPrompt(), bo.getDefaultPrompt(), bo.getMaxReplyLength()));
        runtimeCacheManager.delete(exist.getGoodfishAccountId(), exist.getGoodfishItemId());
        return true;
    }

    /**
     * 删除商品（软删 + 删缓存）.
     */
    @Override
    public Boolean removeItem(Long id) {
        GoodfishItemVo exist = itemManager.requireVoWithPromptById(id);
        boolean flag = itemManager.softDeleteById(id);
        if (flag) {
            runtimeCacheManager.delete(exist.getGoodfishAccountId(), exist.getGoodfishItemId());
        }
        return flag;
    }

    /**
     * 复制提示词（覆盖式；目标全部存在才执行，避免部分成功）.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean copyPrompt(GoodfishItemCopyPromptBo bo) {
        GoodfishItemVo source = itemManager.requireVoWithPromptById(bo.getSourceItemId());
        List<GoodfishItemVo> targets = itemManager.selectVoByIds(bo.getTargetItemIds());
        if (targets.size() != bo.getTargetItemIds().size()) {
            throw new ServiceException(GoodfishItemConstants.MSG_ITEM_NOT_FOUND,
                GoodfishItemConstants.CODE_ITEM_NOT_FOUND);
        }
        // 复制是覆盖语义：源未配置字数时按默认值覆盖目标（其余字段为 null 时保留目标原值）
        Integer sourceMaxReplyLength = source.getMaxReplyLength() == null
            ? GoodfishItemConstants.DEFAULT_MAX_REPLY_LENGTH : source.getMaxReplyLength();
        for (GoodfishItemVo target : targets) {
            promptManager.saveByItemId(buildPrompt(target.getId(), source.getSystemPrompt(),
                source.getPricePrompt(), source.getTechPrompt(), source.getDefaultPrompt(),
                sourceMaxReplyLength));
            runtimeCacheManager.delete(target.getGoodfishAccountId(), target.getGoodfishItemId());
        }
        return true;
    }

    /**
     * 按闲鱼账号 ID 统计商品数.
     */
    @Override
    public long countByAccount(String goodfishAccountId) {
        return itemManager.countByAccount(goodfishAccountId);
    }

    /**
     * 组装提示词实体.
     *
     * <p>null 必须保持 null 传给 Mapper：更新分支靠 MyBatis-Plus 忽略 null 字段
     * 实现「未传不改」，默认最大回复字数只在插入新行时由 manager 回落。
     * 若在此处把 null 换成默认值，用户只改标题时会把已有提示词字数重置为 50.</p>
     */
    private GoodfishItemPrompt buildPrompt(Long itemId, String systemPrompt, String pricePrompt,
                                           String techPrompt, String defaultPrompt, Integer maxReplyLength) {
        GoodfishItemPrompt prompt = new GoodfishItemPrompt();
        prompt.setItemId(itemId);
        prompt.setSystemPrompt(systemPrompt);
        prompt.setPricePrompt(pricePrompt);
        prompt.setTechPrompt(techPrompt);
        prompt.setDefaultPrompt(defaultPrompt);
        prompt.setMaxReplyLength(maxReplyLength);
        return prompt;
    }

}
