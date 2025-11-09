package com.bigbear.goofish;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication(scanBasePackages = {"com.bigbear.goodfish", "com.bigbear.ai"})
public class Starter {
    public static void main(String[] args) {
        SpringApplication.run(Starter.class, args);
    }

}
