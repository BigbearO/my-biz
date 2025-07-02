package com.bigbear.quarkus.core.util;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import jakarta.enterprise.inject.spi.CDI;


public final class QuarkusUtil {

    /**
     * Quarkus 工具类（类似 Hutool SpringUtil）
     */


    private QuarkusUtil() {
        // 私有构造方法，防止实例化
    }

    /**
     * 获取 Bean（如果未找到，返回 null）
     */
    public static <T> T getBean(Class<T> clazz) {
        try (InstanceHandle<T> instance = Arc.container().instance(clazz)) {
            return instance.isAvailable() ? instance.get() : null;
        }
    }

    /**
     * 获取 Bean（如果未找到，抛出异常）
     */
    public static <T> T getRequiredBean(Class<T> clazz) {
        T bean = getBean(clazz);
        if (bean == null) {
            throw new IllegalStateException(
                "No bean of type " + clazz.getName() + " found in Quarkus CDI container");
        }
        return bean;
    }

    /**
     * 获取 Bean（通过名称）
     */
    public static Object getBean(String name) {
        return Arc.container().instance(name).get();
    }

    /**
     * 获取 CDI 容器（直接访问 Arc）
     */
    public static CDI<Object> getCdiContainer() {
        return CDI.current();
    }

}
