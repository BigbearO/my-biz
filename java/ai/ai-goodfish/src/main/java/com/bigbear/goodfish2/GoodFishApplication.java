package com.bigbear.goodfish2;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;


@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.bigbear"})
public class GoodFishApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodFishApplication.class, args);
    }

}
