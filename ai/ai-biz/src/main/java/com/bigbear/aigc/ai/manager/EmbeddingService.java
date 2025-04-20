package com.bigbear.aigc.ai.manager;//package com.bigbear.aigc.ai.manager;
//
//
//import dev.langchain4j.data.embedding.Embedding;
//import dev.langchain4j.data.segment.TextSegment;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.output.Response;
//import dev.langchain4j.store.embedding.EmbeddingMatch;
//import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
//import dev.langchain4j.store.embedding.EmbeddingSearchResult;
//import dev.langchain4j.store.embedding.EmbeddingStore;
//import dev.langchain4j.store.embedding.filter.comparison.IsIn;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.util.CollectionUtils;
//
//import java.util.List;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class EmbeddingService {
//	private final EmbeddingModel openAiEmbeddingModel;
//
//	final EmbeddingStore embeddingStore;
//	public String queryExpression(String interMsg, String fieldMsg) {
//		Response<Embedding> embed = openAiEmbeddingModel.embed(fieldMsg);
//		String interfaceType = getInterfaceType(interMsg);
//		EmbeddingSearchRequest searchRequest = new EmbeddingSearchRequest(embed.content(), 1,
//				0.90, new IsIn("interfaceType", List.of(interfaceType)));
//		EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
//		for (EmbeddingMatch<TextSegment> embeddingMatch : searchResult.matches()) {
//			String expression = embeddingMatch.embedded().metadata().getString("expression");
//			log.info("接口描述:{}, 字段描述:{}, 匹配的表达式:{}", interMsg, fieldMsg, expression);
//			return expression;
//		}
//		return "";
//	}
//
//	private String getInterfaceType(String msg) {
//		Response<Embedding> embed = openAiEmbeddingModel.embed(msg);
//		EmbeddingSearchRequest searchRequest = new EmbeddingSearchRequest(embed.content(), 1,
//				0.99, new IsIn("type", List.of("interface")));
//		EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
//		List<EmbeddingMatch<TextSegment>> result = searchResult.matches();
//		if (CollectionUtils.isEmpty(searchResult.matches())) {
//			return "";
//		}
//		for (EmbeddingMatch<TextSegment> embeddingMatch : result) {
//			String interfaceType = embeddingMatch.embedded().metadata().getString("interfaceType");
//			log.info("接口描述:{}, 匹配的接口类型为：{}", embeddingMatch.embedded().text(), interfaceType);
//			return interfaceType;
//		}
//		return "";
//	}
//}
