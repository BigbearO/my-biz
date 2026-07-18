package com.bigbear.spring.grpc.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * demo 启动类：演示官方 spring-grpc 的「REST 入站 → 身份透传 → gRPC 调用」全链路。
 */
@SpringBootApplication
public class DemoApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
