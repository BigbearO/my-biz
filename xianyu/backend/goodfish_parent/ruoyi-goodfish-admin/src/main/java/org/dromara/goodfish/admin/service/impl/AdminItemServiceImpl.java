package org.dromara.goodfish.admin.service.impl;

import account.v1.Account;
import item.v1.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.goodfish.admin.constants.GoodfishAdminConstants;
import org.dromara.goodfish.admin.domain.vo.AdminItemVo;
import org.dromara.goodfish.admin.manager.AccountClientManager;
import org.dromara.goodfish.admin.manager.ItemClientManager;
import org.dromara.goodfish.admin.service.IAdminItemService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 管理后台商品列表Service业务层实现（业务编排）.
 *
 * <p>数据全部经 gRPC 从基础服务获取，账号名/昵称内存拼装，不跨库 JOIN；
 * 账号服务异常时降级（账号列留空 + WARN），可用性优先.</p>
 *
 * @author goodfish
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AdminItemServiceImpl implements IAdminItemService {

    private final ItemClientManager itemClientManager;
    private final AccountClientManager accountClientManager;

    /**
     * 分页查询商品列表（聚合账号名/昵称）.
     */
    @Override
    public PageResult<AdminItemVo> queryPageList(Integer pageNum, Integer pageSize,
                                                 String goodfishAccountId, String title) {
        int resolvedPageNum = resolvePageNum(pageNum);
        int resolvedPageSize = resolvePageSize(pageSize);

        // 1. gRPC 拉商品分页
        Item.ListItemsResponse itemResponse =
            itemClientManager.listItems(resolvedPageNum, resolvedPageSize, goodfishAccountId, title);

        // 2. gRPC 拉全量账号建索引（失败降级为空 Map，不阻断商品列表）
        Map<String, Account.AccountInfo> accountMap = loadAccountMap();

        // 3. 内存拼装
        List<AdminItemVo> vos = itemResponse.getListList().stream()
            .map(item -> toAdminItemVo(item, accountMap))
            .toList();
        return PageResult.build(vos, itemResponse.getTotal());
    }

    /**
     * 拉取全量账号索引；账号服务不可用时降级为空 Map（账号列留空）.
     */
    private Map<String, Account.AccountInfo> loadAccountMap() {
        try {
            return accountClientManager.listAllAccounts();
        } catch (ServiceException e) {
            log.warn("[{}] 账号服务拉取失败，商品列表账号列降级为空: {}",
                GoodfishAdminConstants.LOGGER_ADMIN_ITEM_SERVICE, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 商品摘要 + 账号信息 → 后台商品 Vo.
     */
    private AdminItemVo toAdminItemVo(Item.ItemSummary item, Map<String, Account.AccountInfo> accountMap) {
        AdminItemVo vo = new AdminItemVo();
        vo.setId(item.getId());
        vo.setGoodfishAccountId(item.getGoodfishAccountId());
        vo.setGoodfishItemId(item.getGoodfishItemId());
        vo.setTitle(item.getTitle());
        vo.setPrice(item.getPrice());
        vo.setStockStatus(item.getStockStatus());
        vo.setLastSyncAt(item.getLastSyncAt());
        Account.AccountInfo account = accountMap.get(item.getGoodfishAccountId());
        if (account != null) {
            vo.setAccountRemark(account.getRemark());
            vo.setAccountNickname(account.getGoodfishNickname());
        }
        return vo;
    }

    /**
     * 页码归一化：未传/非法取默认值 1.
     */
    private int resolvePageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? GoodfishAdminConstants.GRPC_DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 每页条数归一化：未传/非法取默认值，超过上限截断.
     */
    private int resolvePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return GoodfishAdminConstants.GRPC_DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, GoodfishAdminConstants.GRPC_MAX_PAGE_SIZE);
    }

}
