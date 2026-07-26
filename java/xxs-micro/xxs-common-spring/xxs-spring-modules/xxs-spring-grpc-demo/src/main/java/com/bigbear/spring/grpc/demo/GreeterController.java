package com.bigbear.spring.grpc.demo;

import com.bigbear.spring.grpc.greeter.GreeterClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST 入口：演示「HTTP 入站 → 充血 GreeterClient → gRPC 调用」全链路。
 *
 * <p>通道目标 {@code nacos://greeter-grpc} 由 {@code xxs-spring-grpc-nacos} 解析，
 * 经由 Nacos 服务发现定位 gRPC 实例。
 */

@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class GreeterController {

    /** Greeter 充血客户端（由 greeter-api 自动装配）。 */
    private final GreeterClient greeterClient;

    /**
     * 调用 gRPC Greeter 服务并打印回显消息。
     *
     * @param name 问候对象，默认 world
     * @return gRPC 服务回显的消息
     */
    @GetMapping("/hello")
    public String hello(@RequestParam(defaultValue = "world") String name) {
        log.info("say hello start={}", name);
        return greeterClient.sayHello(name);
    }
}
