package com.bigbear.aigc.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;

@Getter
@AllArgsConstructor
public enum AiContentEnums {
	VIDEO("video_url",0),
	IMAGE("image_url",1),
	TEXT("text",2),
	;

	private final String name;
	private final Integer code;

	private static final EnumSet<AiContentEnums> ALL = EnumSet.allOf(AiContentEnums.class);
	public static AiContentEnums of(Integer code) {
		for (AiContentEnums editionEnum : ALL) {
			if (editionEnum.getCode().equals(code)) {
				return editionEnum;
			}
		}

		throw new RuntimeException("unsupported code ");
	}

}
