package com.bigbear.quarkus.nacos.registrar;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.stork.Stork;
import io.smallrye.stork.api.Service;
import io.smallrye.stork.api.ServiceRegistrar;

/**
 * Nacos Stork 自动注册触发器。
 * <p>
 * Quarkus 的 {@code SmallRyeStorkRegistrationRecorder} 仅在 classpath 存在 Consul/Eureka/Static 内置注册器时
 * 才自动触发注册，自定义的 {@code nacos} 注册器不在此硬编码 gate 内，故需本 Bean 主动触发：
 * <ul>
 *   <li>应用启动完成后遍历 Stork 全部服务，对属于 {@link NacosServiceRegistrar} 的服务调用其 {@code registerNow()} 完成注册；</li>
 *   <li>应用关闭前遍历同类服务调用 {@code deregisterServiceInstance(...)} 精确注销。</li>
 * </ul>
 * 本 Bean 仅处理 {@code nacos} 类型注册器，与 Quarkus 内置注册器的触发逻辑互不重叠，不会重复注册。
 *
 * @author xxs-micro
 */
@ApplicationScoped
public class NacosStorkRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(NacosStorkRegistration.class);

    /**
     * 应用启动完成后触发 Nacos 注册。
     * <p>
     * 遍历 Stork 全部服务，仅对 {@link NacosServiceRegistrar} 实例执行注册；注册失败仅记录错误，不阻塞启动。
     *
     * @param event Quarkus 启动完成事件
     */
    void onStart(@Observes StartupEvent event) {
        for (Map.Entry<String, Service> entry : Stork.getInstance().getServices().entrySet()) {
            ServiceRegistrar<?> registrar = entry.getValue().getServiceRegistrar();
            if (registrar instanceof NacosServiceRegistrar nacos) {
                nacos.registerNow().subscribe().with(
                        v -> LOG.info("Nacos 服务注册成功: service={}", entry.getKey()),
                        err -> LOG.error("Nacos 服务注册失败: service={}", entry.getKey(), err));
            }
        }
    }

    /**
     * 应用关闭前触发 Nacos 注销。
     * <p>
     * 遍历 Stork 全部服务，仅对 {@link NacosServiceRegistrar} 实例执行注销；注销失败仅告警，不影响关闭流程。
     *
     * @param event Quarkus 关闭事件
     */
    void onStop(@Observes ShutdownEvent event) {
        for (Map.Entry<String, Service> entry : Stork.getInstance().getServices().entrySet()) {
            ServiceRegistrar<?> registrar = entry.getValue().getServiceRegistrar();
            if (registrar instanceof NacosServiceRegistrar nacos) {
                try {
                    nacos.deregisterServiceInstance(nacos.getServiceName()).await().indefinitely();
                } catch (Exception ex) {
                    LOG.warn("Nacos 服务注销失败(不影响关闭): service={}", entry.getKey(), ex);
                }
            }
        }
    }
}
