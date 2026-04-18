package com.bigbear.ai.platform.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可用模型列表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelsResponse {

    private String current;

    private List<String> available;
}
