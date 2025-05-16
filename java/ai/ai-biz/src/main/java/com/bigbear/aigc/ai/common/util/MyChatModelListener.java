package com.bigbear.aigc.ai.common.util;

import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;


@Slf4j
public class MyChatModelListener implements ChatModelListener {
	@Override
	public void onRequest(ChatModelRequestContext requestContext) {

		ChatRequest request = requestContext.chatRequest();
		Map<Object, Object> attributes = requestContext.attributes();
		// 记录请求
	}

	@Override
	public void onResponse(ChatModelResponseContext responseContext) {
		ChatResponse response = responseContext.chatResponse();
		ChatRequest request = responseContext.chatRequest();
		Map<Object, Object> attributes = responseContext.attributes();
		// 记录响应
	}

	@Override
	public void onError(ChatModelErrorContext errorContext) {
		Throwable error = errorContext.error();
		ChatRequest request = errorContext.chatRequest();
		Map<Object, Object> attributes = errorContext.attributes();
		// 记录错误
		log.info("出错={},msg={}", error.getMessage(),JSONObject.toJSONString(attributes));
	}

}
