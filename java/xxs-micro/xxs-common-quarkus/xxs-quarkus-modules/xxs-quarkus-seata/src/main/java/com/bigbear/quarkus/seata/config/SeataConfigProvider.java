package com.bigbear.quarkus.seata.config;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Optional;

import org.apache.seata.common.util.StringUtils;
import org.apache.seata.config.Configuration;
import org.apache.seata.config.ExtConfigurationProvider;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Seata 配置桥接 SPI：将 Seata 内部按裸 key（如 {@code registry.type}、{@code service.vgroupMapping.xxx}）
 * 发起的配置读取，桥接为 Quarkus MicroProfile Config 下的 {@code seata.*} 键。
 *
 * <p>Seata 通过 {@code META-INF/services/org.apache.seata.config.ExtConfigurationProvider} 发现本实现，
 * 在原有文件配置之上叠加一层动态代理：读不到时回退原实现与默认值，读得到则优先返回
 * {@code seata.<dataId>}（含驼峰转中划线/下划线的变体）对应的值。
 *
 * <p>以 {@code java.lang.reflect.Proxy} 实现动态代理，避免额外引入 ByteBuddy 依赖。
 *
 * @author xxs-micro
 */
public class SeataConfigProvider implements ExtConfigurationProvider {

    /** 拦截以 get 开头且首个参数为配置 key 的读取方法 */
    private static final String GET_METHOD_PREFIX = "get";

    @Override
    public Configuration provide(Configuration originalConfiguration) {
        return (Configuration) Proxy.newProxyInstance(
                Configuration.class.getClassLoader(),
                new Class<?>[] {Configuration.class},
                new ConfigurationBridge(originalConfiguration));
    }

    /**
     * 配置桥接的调用处理器。
     */
    private static final class ConfigurationBridge implements InvocationHandler {

        private final Configuration original;

        private ConfigurationBridge(Configuration original) {
            this.original = original;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (isReadMethod(method, args)) {
                String dataId = (String) args[0];
                Object fallback = args.length > 1 ? args[1] : null;
                Optional<String> value = resolve(dataId);
                // TODO temp diag
                if ("service.vgroupMapping.greeter-group".equals(dataId)
                        || "service.grouplist.default".equals(dataId)) {
                    org.slf4j.LoggerFactory.getLogger(SeataConfigProvider.class)
                            .warn("DIAG proxy read dataId=[{}] method=[{}] present=[{}] value=[{}]",
                                    dataId, method.getName(), value.isPresent(), value.orElse("<empty>"));
                }
                if (value.isPresent()) {
                    return convert(method.getReturnType(), value.get(), fallback);
                }
            }
            return method.invoke(original, args);
        }

        /**
         * 是否为可拦截的配置读取方法：以 get 开头且首个参数为字符串 key。
         *
         * @param method 方法
         * @param args   参数
         * @return true 表示命中
         */
        private boolean isReadMethod(Method method, Object[] args) {
            return method.getName().startsWith(GET_METHOD_PREFIX)
                    && args != null
                    && args.length > 0
                    && args[0] instanceof String;
        }

        /**
         * 从 MicroProfile Config 解析 {@code seata.<dataId>}，兼容驼峰/下划线/中划线变体。
         *
         * @param dataId Seata 内部裸 key
         * @return 配置值（未配置则空）
         */
        private Optional<String> resolve(String dataId) {
            Config config = ConfigProvider.getConfig();
            String line = StringUtils.hump2Line(dataId).replace('_', '-');
            String kebab = dataId.replace('_', '-');
            return config.getOptionalValue(SeataConfigConstants.SEATA_PREFIX + "." + dataId, String.class)
                    .or(() -> config.getOptionalValue(SeataConfigConstants.SEATA_PREFIX + "." + kebab, String.class))
                    .or(() -> config.getOptionalValue(SeataConfigConstants.SEATA_PREFIX + "." + line, String.class));
        }

        /**
         * 将字符串配置值转换为方法期望的返回类型；不支持的类型回退原实现。
         *
         * @param returnType 方法返回类型
         * @param value      配置值
         * @param fallback   原方法默认值参数（用于转换失败时的兜底）
         * @return 转换后的值
         */
        private Object convert(Class<?> returnType, String value, Object fallback) {
            if (returnType == String.class) {
                return value;
            }
            if (returnType == boolean.class || returnType == Boolean.class) {
                return Boolean.parseBoolean(value);
            }
            if (returnType == int.class || returnType == Integer.class) {
                return Integer.parseInt(value.trim());
            }
            if (returnType == long.class || returnType == Long.class) {
                return Long.parseLong(value.trim());
            }
            if (returnType == short.class || returnType == Short.class) {
                return Short.parseShort(value.trim());
            }
            if (returnType == Duration.class) {
                return parseDuration(value);
            }
            return fallback;
        }

        /**
         * 解析时长配置：纯数字按毫秒，否则按 ISO-8601 时长解析。
         *
         * @param value 配置值
         * @return Duration
         */
        private Duration parseDuration(String value) {
            try {
                return Duration.ofMillis(Long.parseLong(value.trim()));
            } catch (NumberFormatException e) {
                return Duration.parse(value.trim());
            }
        }
    }
}
