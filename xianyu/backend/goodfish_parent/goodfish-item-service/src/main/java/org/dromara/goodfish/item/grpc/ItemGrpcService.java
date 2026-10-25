package org.dromara.goodfish.item.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import item.v1.Item;
import item.v1.ItemServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;
import org.dromara.goodfish.item.domain.bo.GoodfishItemQueryBo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;
import org.dromara.goodfish.item.service.IGoodfishItemService;
import org.dromara.goodfish.item.service.IItemRuntimeConfigService;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;

/**
 * 商品域 gRPC 服务实现（ItemService）.
 *
 * <p>由 Spring Boot 4.1 官方 spring-grpc 的 {@link GrpcService} 自动注册；
 * 监听端口经 xxs-spring-grpc-nacos 以 {@code <appName>-grpc} 双注册到 Nacos.
 * 商品删除后返回 NOT_FOUND（对话服务映射 40401，客户端不回复）.</p>
 *
 * @author goodfish
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ItemGrpcService extends ItemServiceGrpc.ItemServiceImplBase {

    private final IItemRuntimeConfigService runtimeConfigService;
    private final IGoodfishItemService itemService;

    /**
     * 单商品运行时配置（对话服务主用）.
     */
    @Override
    public void getItemRuntimeConfig(Item.GetItemRuntimeConfigRequest request,
                                     StreamObserver<Item.ItemRuntimeConfig> responseObserver) {
        try {
            GoodfishItemVo vo = runtimeConfigService.getRuntimeConfig(request.getAccountId(), request.getItemId());
            if (vo == null) {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription(GoodfishItemConstants.MSG_ITEM_NOT_FOUND).asRuntimeException());
                return;
            }
            responseObserver.onNext(toRuntimeConfig(vo));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[{}] getItemRuntimeConfig 失败 accountId={}, itemId={}",
                GoodfishItemConstants.LOGGER_ITEM_GRPC, request.getAccountId(), request.getItemId(), e);
            responseObserver.onError(Status.INTERNAL.withCause(e)
                .withDescription(e.getMessage()).asRuntimeException());
        }
    }

    /**
     * 批量运行时配置（预留：后台批量测试 / 预热）.
     */
    @Override
    public void batchGetItemRuntimeConfig(Item.BatchGetItemRuntimeConfigRequest request,
                                          StreamObserver<Item.BatchGetItemRuntimeConfigResponse> responseObserver) {
        try {
            List<GoodfishItemVo> list = runtimeConfigService.batchGetRuntimeConfig(
                request.getAccountId(), request.getItemIdsList());
            Item.BatchGetItemRuntimeConfigResponse.Builder builder =
                Item.BatchGetItemRuntimeConfigResponse.newBuilder();
            for (GoodfishItemVo vo : list) {
                builder.addConfigs(toRuntimeConfig(vo));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[{}] batchGetItemRuntimeConfig 失败 accountId={}",
                GoodfishItemConstants.LOGGER_ITEM_GRPC, request.getAccountId(), e);
            responseObserver.onError(Status.INTERNAL.withCause(e)
                .withDescription(e.getMessage()).asRuntimeException());
        }
    }

    /**
     * 商品列表（admin BFF 商品列表页聚合账号名用）.
     */
    @Override
    public void listItems(Item.ListItemsRequest request,
                          StreamObserver<Item.ListItemsResponse> responseObserver) {
        try {
            GoodfishItemQueryBo queryBo = new GoodfishItemQueryBo();
            queryBo.setGoodfishAccountId(request.getGoodfishAccountId());
            queryBo.setTitle(request.getTitle());
            int pageNum = request.getPage() > 0 ? request.getPage() : PageQuery.DEFAULT_PAGE_NUM;
            int pageSize = request.getPageSize() <= 0
                ? GoodfishItemConstants.GRPC_DEFAULT_PAGE_SIZE
                : Math.min(request.getPageSize(), GoodfishItemConstants.GRPC_MAX_PAGE_SIZE);
            var result = itemService.queryPageList(queryBo, new PageQuery(pageSize, pageNum));

            Item.ListItemsResponse.Builder builder = Item.ListItemsResponse.newBuilder()
                .setTotal(result.getTotal());
            for (GoodfishItemVo vo : result.getRows()) {
                builder.addList(toItemSummary(vo));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[{}] listItems 失败", GoodfishItemConstants.LOGGER_ITEM_GRPC, e);
            responseObserver.onError(Status.INTERNAL.withCause(e)
                .withDescription(e.getMessage()).asRuntimeException());
        }
    }

    /**
     * 视图对象转列表摘要消息.
     */
    private Item.ItemSummary toItemSummary(GoodfishItemVo vo) {
        return Item.ItemSummary.newBuilder()
            .setId(vo.getId() == null ? 0L : vo.getId())
            .setGoodfishAccountId(nullToEmpty(vo.getGoodfishAccountId()))
            .setGoodfishItemId(nullToEmpty(vo.getGoodfishItemId()))
            .setTitle(nullToEmpty(vo.getTitle()))
            .setPrice(vo.getPrice() == null ? "" : vo.getPrice().toPlainString())
            .setStockStatus(nullToEmpty(vo.getStockStatus()))
            .setLastSyncAt(vo.getSyncTime() == null ? ""
                : vo.getSyncTime().format(GoodfishItemConstants.DATE_TIME_FORMATTER))
            .build();
    }

    /**
     * 视图对象转契约消息.
     */
    private Item.ItemRuntimeConfig toRuntimeConfig(GoodfishItemVo vo) {
        Item.PromptConfig prompt = Item.PromptConfig.newBuilder()
            .setSystemPrompt(nullToEmpty(vo.getSystemPrompt()))
            .setPricePrompt(nullToEmpty(vo.getPricePrompt()))
            .setTechPrompt(nullToEmpty(vo.getTechPrompt()))
            .setDefaultPrompt(nullToEmpty(vo.getDefaultPrompt()))
            .setMaxReplyLength(vo.getMaxReplyLength() == null ? 0 : vo.getMaxReplyLength())
            .build();
        return Item.ItemRuntimeConfig.newBuilder()
            .setItemId(nullToEmpty(vo.getGoodfishItemId()))
            .setTitle(nullToEmpty(vo.getTitle()))
            .setDescription(nullToEmpty(vo.getDescription()))
            // 价格用 string 避免浮点精度问题（如 199.90）
            .setPrice(vo.getPrice() == null ? "" : vo.getPrice().toPlainString())
            .setStockStatus(nullToEmpty(vo.getStockStatus()))
            .setImageUrl(nullToEmpty(vo.getImageUrl()))
            .setPrompt(prompt)
            .build();
    }

    /**
     * proto3 字符串字段不接受 null，统一转空串.
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

}
