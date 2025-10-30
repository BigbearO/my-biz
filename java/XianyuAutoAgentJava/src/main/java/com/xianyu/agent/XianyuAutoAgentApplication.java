package com.xianyu.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class XianyuAutoAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(XianyuAutoAgentApplication.class, args);
    }

}