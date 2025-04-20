package com.bigbear.aigc.ai.common.util;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.bigbear.aigc.ai.common.enums.InterfaceTypeEnum;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class FundDocumentSplitter implements DocumentSplitter {
	@Override
	public List<TextSegment> split(Document document) {
		List<TextSegment> segments = new ArrayList<>();
		String[] parts = document.text().split("\n");
		for (String part : parts) {
			try {
				String[] splits = part.split("\\@@");
				if (splits.length != 3) {
					continue;
				}
				if (StringUtils.isBlank(splits[2])) {
					continue;
				}
				TextSegment textSegment = TextSegment.from(splits[1]);
				packageMetadata(textSegment, splits[0], splits[2]);
				segments.add(textSegment);
			} catch (Exception e) {
				log.error("数据分割异常part:{}, msg:{}",part, e.getMessage());
			}
		}
		return segments;
	}

	private void packageMetadata(TextSegment textSegment, String interfaceDesc, String expression) {
		textSegment.metadata().put("interfaceType", getInterfaceType(interfaceDesc)); // 接口类型
		textSegment.metadata().put("expression", expression); // 表达式
	}

	private String getInterfaceType(String interfaceDesc) {
		Map<String, InterfaceTypeEnum> map = InterfaceTypeEnum.map;
		for (Map.Entry<String, InterfaceTypeEnum> entry : map.entrySet()) {
			if (entry.getValue().getSuptDesc().contains(interfaceDesc)) {
				return entry.getKey();
			}
		}
		return "";
	}
}
