package com.bigbear.aigc.ai.manager;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;

public class CharTokenizer implements Tokenizer {
	//	todo 计算就是最简单，按照字符数，1个token2个字符
	@Override
	public int estimateTokenCountInText(String s) {
		if (s == null || s.isEmpty()) {
			return 0;
		}
		double v = s.length() / 2.0;

		return (int) Math.ceil(v);
	}


	@Override
	public int estimateTokenCountInMessage(ChatMessage chatMessage) {
		return 0;
	}

	@Override
	public int estimateTokenCountInMessages(Iterable<ChatMessage> iterable) {
		return 0;
	}


}
