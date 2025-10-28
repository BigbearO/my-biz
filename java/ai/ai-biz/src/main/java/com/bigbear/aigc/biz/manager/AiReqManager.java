package com.bigbear.aigc.biz.manager;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSONObject;
import com.bigbear.aigc.biz.common.property.LLMProperties;
import com.bigbear.aigc.biz.domain.dto.AiReqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReqManager {

	protected final LLMProperties llmProperties;
	//视频识别参考 https://help.aliyun.com/zh/model-studio/developer-reference/qwenvl-video-understanding#7248f71e3eoy9
	public   String getStr(AiReqDTO reqDTO){
		String serverUrl = llmProperties.getBaseUrl() + llmProperties.getMediaPath();
		String jsonString = JSONObject.toJSONString(reqDTO);
		log.info("xx={},body={}",serverUrl,jsonString);
		return HttpRequest.post(serverUrl)
//				.header("Authorization", "Bearer bedf4894-4851-4834-b097-f7bad1d77ffe") 公司的
				.header("Authorization", "Bearer "+llmProperties.getAk())
				.header("Content-Type", "application/json")
				.timeout(5000)
				.setConnectionTimeout(5000)
				.setReadTimeout(5000)
				.body(jsonString)
				.execute()
				.body();
	}

	public JSONObject getJson(AiReqDTO reqDTO){
		return JSONObject.parseObject(getStr(reqDTO));
	}
}
