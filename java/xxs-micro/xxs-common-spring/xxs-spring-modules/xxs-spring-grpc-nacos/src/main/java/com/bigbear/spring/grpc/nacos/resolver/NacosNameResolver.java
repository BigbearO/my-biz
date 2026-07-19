package com.bigbear.spring.grpc.nacos.resolver;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.bigbear.spring.grpc.nacos.convert.NacosInstanceConverter;
import com.bigbear.spring.grpc.nacos.properties.NacosNamingProperties;
import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Nacos 的 gRPC {@link NameResolver}。
 *
 * <p>解析 {@code nacos://<serviceName>} 目标：从 Nacos 拉取健康实例并转换为等价地址组；
 * 若开启订阅（默认开启），实例上下线将热刷新到对应 gRPC Channel。
 *
 * <p>由于采用 {@code -grpc} 后缀的专用服务名区分 gRPC 与 HTTP 端口，无需读取实例 metadata 探测端口，
 * 直接使用 {@link Instance#getPort()} 即可。
 */
public class NacosNameResolver extends NameResolver {

    private static final Logger log = LoggerFactory.getLogger(NacosNameResolver.class);

    /** Nacos 目标 URI（nacos://<serviceName>）。 */
    private final URI uri;

    /** Nacos 命名服务。 */
    private final NamingService namingService;

    /** Nacos 命名配置。 */
    private final NacosNamingProperties properties;

    /** 解析出的 Nacos 服务名（URI 的 host 部分）。 */
    private final String serviceName;

    /** gRPC 监听器，用于推送地址与错误。 */
    private Listener2 listener;

    /** Nacos 订阅回调，持有引用以便注销。 */
    private final EventListener nacosListener = this::onNacosEvent;

    /**
     * 构造 Nacos NameResolver。
     *
     * @param uri          目标 URI（nacos://<serviceName>）
     * @param namingService Nacos 命名服务
     * @param properties    Nacos 命名配置
     */
    public NacosNameResolver(URI uri, NamingService namingService, NacosNamingProperties properties) {
        this.uri = uri;
        this.namingService = namingService;
        this.properties = properties;
        this.serviceName = uri.getHost();
    }

    @Override
    public String getServiceAuthority() {
        return uri.getAuthority();
    }

    @Override
    public void start(Listener2 listener) {
        this.listener = listener;
        resolve();
        if (properties.isSubscribe()) {
            try {
                namingService.subscribe(serviceName, properties.getGroup(), nacosListener);
            } catch (Exception e) {
                // 订阅失败不阻断首次解析结果，仅失去热刷新能力
                log.warn("订阅 Nacos 服务[{}]变更失败，将仅使用首次解析结果", serviceName, e);
            }
        }
    }

    /**
     * 从 Nacos 拉取实例并推送给 gRPC 监听器。
     */
    private void resolve() {
        try {
            List<Instance> instances = namingService.getAllInstances(serviceName, properties.getGroup());
            List<EquivalentAddressGroup> groups =
                NacosInstanceConverter.toAddressGroups(instances, properties.isHealthyOnly());
            listener.onAddresses(groups, Attributes.EMPTY);
        } catch (Exception e) {
            log.error("从 Nacos 解析服务[{}]实例失败", serviceName, e);
            listener.onError(Status.UNAVAILABLE.withCause(e).withDescription("nacos resolve failed: " + serviceName));
        }
    }

    /**
     * Nacos 实例变更事件回调，触发重新解析。
     *
     * @param event Nacos 事件
     */
    private void onNacosEvent(Event event) {
        if (event instanceof NamingEvent) {
            log.debug("收到 Nacos 服务[{}]实例变更，重新解析", serviceName);
            resolve();
        }
    }

    @Override
    public void shutdown() {
        if (properties.isSubscribe()) {
            try {
                namingService.unsubscribe(serviceName, properties.getGroup(), nacosListener);
            } catch (Exception e) {
                log.debug("取消订阅 Nacos 服务[{}]失败", serviceName, e);
            }
        }
    }
}
