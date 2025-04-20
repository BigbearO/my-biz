package com.bigbear.aigc.ai.controller;

import com.bigbear.aigc.ai.service.ChatService;
import com.bigbear.aigc.ai.service.MediaContentRecognition;
import com.bigbear.aigc.ai.service.PetDoctor;
import com.pig4cloud.pig.common.core.util.R;
import com.pig4cloud.pig.common.security.annotation.Inner;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/test")
@Tag(description = "test", name = "test")
@Inner(value = false)
public class TestController {


	private final ChatService chatService;

	private final MediaContentRecognition mediaContentRecognition;

	private final PetDoctor petDoctor;

	@RequestMapping("/chat")
	@Inner(value = false)
	public R<?> chat(@RequestParam String question) {
		return R.ok(petDoctor.chat(1L,question));
	}

	@RequestMapping("/contentRecognition")
	@Inner(value = false)
	public R<?> contentRecognition(@RequestParam String url, @RequestParam(required = false, defaultValue = "0")
	Integer type, @RequestParam String question) {
		url = url.trim();
		url = url.replace(" ", "");
		return R.ok(mediaContentRecognition.contentByWebUrl(type,url, question));
	}

	@RequestMapping("/test")
	@Inner(value = false)
	public R<?> test() {
		String res = "";
		try {
			res = mediaContentRecognition.contentByBytes("提取图片中 9月30号的上证指数点数", null);
		} catch (IOException e) {

		}
		return R.ok(res);
	}

	public static void main(String[] args) {
		String s="xxx5 年java 开发经验， 熟悉Vue，具备完整前后端项目开发能力，有一定项目管理经验，有敏捷发布的团队工作经验。有利用新技术实现业务增收的经验-宠物观察报告。\n" +
				"xxx有JVM日志,火焰图分析经验，有根据火焰图优化代码和JVM调优实践经验。\n" +
				"xxx具备实际项目架构设计落地能力，熟悉面向对象设计,有过高并发功能架构设计实践。在华安视讯公司期间参与人脸摄像机设备实时管理项目、宠物摄像机项目的架构、搭建与落地。负责了公司宠物项目抢领打折卡活动-高并发功能的架构设计与落地。\n" +
				"xxx有实际大型项目复杂逻辑分析、设计、编程经验，具备功能设计，架构设计经验。在宠物摄像机项目中，从0到1梳理，分析，设计了成片模型流程，素材数据模型和素材分镜匹配算法，落地成片功能系统。\n" +
				"xxx参与并完成了公司平台核心业务的各个指标接入与监控，系统服务的部分关键指标、慢接口监控、预警及优化。\n" +
				"xxx具备设计模式开发与应用经验，掌握常见的算法。具备项目重构能力和实际经验，例如使用Spring 标准事件、自定义事件即观察者模式发布异步事件、利用策略模式完成成片素材加工等。\n" +
				"xxx了解大模型应用开发，有大模型Agent，RAG应用实践，了解大模型微调。曾基于大模型Agent开发宠物医生功能，基于RAG开发APP官方客服助手。\n" +
				"xxx熟悉 Spring、Spring Boot、Spring Cloud Alibaba的使用及设计原理，使用Gateway转发请求,对限流,负载均衡,分布式事务有实际经验。有starter封装经验,自定义注解经验, (XXL-JOB任务自动注册,POJO转换等)\n" +
				"xxx熟练运用 Linux 常用命令和Arthas快速定位生产、应用等问题，具备解决疑难问题的能力。有Arthas热部署经验,Arthas追踪慢接口并进行性能优化经验\n" +
				"xxxMySQL熟悉,对MySQL底层原理,如MVCC,Innodb,索引等有一定了解,对SQL语句优化有实践经验,解决宠物摄像机项目素材查询深分页问题(8s优化至1s以内)\n" +
				"xxxRabbitMQ 熟练使用,对消息可靠性投递、幂等消费、持久化、重试机制有实际经验。\n" +
				"xxxRedis 熟练使用,string,hash等数据结构熟练使用,对缓存一致性有一定的经验.了解Redisson分布式锁\n" +
				"xxxNginx熟练使用,进行了公司管理页面和h5小程序的部署,以及后端请求的转发\n" +
				"xxx熟悉Docker/K8s的使用, 用docker 搭建开发和测试环境中间件,能进行k8s应用部署\n" +
				"xxx了解Python\n" +
				"xxx熟练使用常见的建模、文档编写等工具来编写项目需求技术设计等文档。";
		String[] split = s.split("\n");
		int i=1;
		for (String str : split) {

			String xxx = str.replace("xxx", i + ". ");
			System.out.println(xxx);
			i++;
		}

	}
}
