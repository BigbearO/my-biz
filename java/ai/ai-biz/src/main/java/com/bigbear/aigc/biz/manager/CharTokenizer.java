package com.bigbear.aigc.biz.manager;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;


public class CharTokenizer implements TokenCountEstimator {
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
