package org.dromara.goodfish.meta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * goodfish 客户端元数据服务
 *
 * <p>闲鱼账号元数据（云端不存储 Cookie）。本期只实现账号域；版本域（client_version）后续迭代。</p>
 *
 * @author goodfish
 */
@SpringBootApplication
public class GoodfishClientMetaApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(GoodfishClientMetaApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  goodfish 客户端元数据服务启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }

}
