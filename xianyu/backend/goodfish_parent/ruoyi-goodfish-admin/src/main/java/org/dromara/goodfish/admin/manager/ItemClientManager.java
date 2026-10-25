package org.dromara.goodfish.admin.manager;

import io.grpc.StatusRuntimeException;
import item.v1.Item;
import item.v1.ItemServiceGrpc;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.goodfish.admin.constants.GoodfishAdminConstants;
import org.springframework.stereotype.Component;

/**
 * 商品服务 gRPC 客户端原子操作层（manager）.
 *
 * @author goodfish
 */
@Component
@RequiredArgsConstructor
public class ItemClientManager {

    private final ItemServiceGrpc.ItemServiceBlockingStub itemStub;

    /**
     * 分页查询商品列表（后台商品列表页数据源）.
     */
    public Item.ListItemsResponse listItems(int pageNum, int pageSize, String goodfishAccountId, String title) {
        Item.ListItemsRequest.Builder builder = Item.ListItemsRequest.newBuilder()
            .setPage(pageNum)
            .setPageSize(pageSize);
        if (StringUtils.isNotBlank(goodfishAccountId)) {
            builder.setGoodfishAccountId(goodfishAccountId);
        }
        if (StringUtils.isNotBlank(title)) {
            builder.setTitle(title);
        }
        try {
            return itemStub.listItems(builder.build());
        } catch (StatusRuntimeException e) {
            throw GrpcClientErrors.map(e, GoodfishAdminConstants.DESC_ITEM_SERVICE,
                GoodfishAdminConstants.CODE_NOT_FOUND);
        }
    }

}
