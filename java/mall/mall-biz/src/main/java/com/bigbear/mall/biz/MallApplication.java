package com.bigbear.mall.biz;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Mall 业务模块启动类
 *
 * @author bigbear
 * @date 2025-02-09
 */


@EnableDiscoveryClient
@SpringBootApplication
public class MallApplication {

    public static void main(String[] args) {

        SpringApplication.run(MallApplication.class, args);
    }
}
