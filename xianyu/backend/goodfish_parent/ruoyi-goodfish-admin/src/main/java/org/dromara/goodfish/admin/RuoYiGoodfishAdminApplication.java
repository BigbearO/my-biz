package org.dromara.goodfish.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * goodfish 管理后台服务（BFF）
 *
 * <p>跨域聚合读，无自有表；数据全部经 gRPC 从商品/模型/客户端元数据服务获取。</p>
 *
 * @author goodfish
 */
@SpringBootApplication
public class RuoYiGoodfishAdminApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(RuoYiGoodfishAdminApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  goodfish 管理后台 BFF 启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }

}
