package com.bigbear.aigc.ai.entity;

import java.util.List;
import java.util.Map;

public class ArkModel {
	String model;
	List<Map<String, String>> messages;
	Integer max_tokens;

	// 就是添加几个下面这种,火山那边包含 assistant,system,user,先是系统级别角色，最高指令那一段
//	然后    assistant，小皮收到 那一段 //     initData.put("role", "assistant");initData.put("content", "小皮收到");
	//最后才是user级别  put("role", "user"); 的四个问题，content就是string了
	// token 计算就是最简单，字符数，1个token2个字符

	/*{
    "role": "user",
    "content": [
        {
          "type": "image_url", //object类型，目前仅支持image_url
          "image_url": {
            "url": "",
            "image_bytes": "",
            "detail": "",
          }
        }
    ]
  }
  */
}
