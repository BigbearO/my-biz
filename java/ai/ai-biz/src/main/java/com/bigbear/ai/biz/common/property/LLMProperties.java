package com.bigbear.ai.biz.common.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "llm")
public class LLMProperties {
	// todo 后期搞成多map的
	private String sk;
	private String ak;
	//		// 免费api列表
	//https://open.bigmodel.cn/api/paas/v4/
			//model="glm-4"
			//https://api.qianwen.aliyun.com/v1/chat/completions
			//https://api.siliconflow.cn/v1
			//		https://spark-api-open.xf-yun.com/v1
	private String baseUrl="https://dashscope.aliyuncs.com/compatible-mode/v1";
	private String mediaPath="/chat/completions";

	private List<Object> list;
}
