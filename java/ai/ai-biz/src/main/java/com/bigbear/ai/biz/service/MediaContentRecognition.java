package com.bigbear.ai.biz.service;

import com.bigbear.ai.biz.domain.dto.AiReqDTO;
import com.bigbear.aigc.domain.enums.AiContentEnums;
import com.bigbear.ai.biz.infra.manager.AiReqManager;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaContentRecognition {
	@jakarta.annotation.Resource(name = "mediaContentRecognitionModel")
	private ChatModel mediaContentRecognitionModel;

	@jakarta.annotation.Resource(name = "qwenVLModel")
	private ChatModel qwenVLModel;

	private final AiReqManager aiReqManager;

	public String contentByBytes(String question, byte[] imageBytes) throws IOException {
		if (imageBytes == null) {
			question = "提取图片中 9月30号的上证指数点数";
			ClassPathResource classPathResource = new ClassPathResource("1.png");
			InputStream inputStream = classPathResource.getInputStream();
			imageBytes = inputStream.readAllBytes();
		}
		String encodeToString = Base64.getEncoder().encodeToString(imageBytes);
		UserMessage userMessage = UserMessage.from(TextContent.from(question),
				ImageContent.from(encodeToString, "image/png"));
		ChatResponse response = mediaContentRecognitionModel.chat(userMessage);
		val text = response.aiMessage().text();

		log.info("content={}", text);
		return text;
	}


	public String contentByWebUrl(Integer type, String url, String question) {


		String text;
		//todo openai   目前不支持video,后面再说
		text=	getByLangchain(url,question);

		log.info("res={}",text);

		return text;
	}

	private String getByWebReq(Integer type, String url, String question) {
		AiContentEnums aiContentEnums = AiContentEnums.of(type);
		AiReqDTO reqDTO = AiReqDTO.mediaFromUrl(aiContentEnums, url, question);

		return aiReqManager.getStr(reqDTO);
	}

	private String getByLangchain(String url, String question) {
		VideoContent video = VideoContent.from(url);
		UserMessage userMessage = UserMessage.from(TextContent.from(question),
				video);
		ChatResponse response = qwenVLModel.chat(userMessage);
		val text = response.aiMessage().text();
		log.info("content={}", text);
		return text;
	}
}
