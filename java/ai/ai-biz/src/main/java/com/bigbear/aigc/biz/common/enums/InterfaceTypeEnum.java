package com.bigbear.aigc.biz.common.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// 枚举类
@Getter
public enum InterfaceTypeEnum {
	CREDIT("creditApply", "预审", List.of("授信申请", "授信查询", "用信申请")),
	LOAN("loanApply", "放款", List.of("借款申请", "借款结果查询", "放款申请"));
	private String code;
	private String desc;
	private List<String> suptDesc;

	InterfaceTypeEnum(String code, String desc, List<String> suptDesc) {
		this.code = code;
		this.desc = desc;
		this.suptDesc = suptDesc;
	}

	public static Map<String, InterfaceTypeEnum> map = new HashMap<>();

	static {
		Stream.of(values()).forEach(v -> map.put(v.getCode(), v));
	}

}

