package org.dromara.goodfish.item;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * goodfish 商品服务
 *
 * <p>商品同步落库、商品查询、提示词配置（与商品一对一）、提示词模板（内置只读）。</p>
 *
 * @author goodfish
 */
@SpringBootApplication
public class GoodfishItemApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(GoodfishItemApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  goodfish 商品服务启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }

}
