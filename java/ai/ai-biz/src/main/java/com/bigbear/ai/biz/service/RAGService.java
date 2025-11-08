package com.bigbear.ai.biz.service;//package com.bigbear.aigc.ai.service;
//
//import com.alibaba.nacos.common.utils.ArrayUtils;
//import dev.langchain4j.data.document.Document;
//import dev.langchain4j.data.document.parser.TextDocumentParser;
//import dev.langchain4j.data.document.splitter.DocumentSplitters;
//import dev.langchain4j.data.embedding.Embedding;
//import dev.langchain4j.data.segment.TextSegment;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.openai.OpenAiTokenizer;
//import dev.langchain4j.store.embedding.EmbeddingStore;
//import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class RAGService {
//	final EmbeddingModel embeddingModel;
//	final EmbeddingStore<TextSegment> embeddingStore;
//	public void rag() {
//		Embedding queryEmbedding = embeddingModel.embed("host报错请检查环境").content();
//	}
//
//	public void rag2(String uuid,String ext,String pathName) {
//		//转化文档格式
//		Document doc = transferExt(ext, pathName);
//
//		//使用 Store嵌入
//		EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
//
//				// 使用指定的 DocumentTransformer 转换文档。
//				.documentTransformer(document -> {
//					//放置元数据uuid，进行标识
//					document.metadata().put("uuid", uuid);
//					return document;
//				})
//
//				// 使用指定的 DocumentSplitter 将文档拆分为 TextSegments，1000是segment的最大token,200是上下文重叠区间
//				.documentSplitter(DocumentSplitters.recursive(1000, 200, new OpenAiTokenizer("")))
//				.embeddingModel(embeddingModel)
//				.embeddingStore(embeddingStore)
//				.build();
//		ingestor.ingest(doc);
//	}
//
//
//
//	//转化文档格式方法
//	private static Document transferExt(String ext, String pathName) {
//		Document document;
//		if (ext.equalsIgnoreCase("txt")) {
//			document = loadDocument(pathName, new TextDocumentParser());
//		}
////		else if (ext.equalsIgnoreCase("pdf")) {
////			document = loadDocument(pathName, new ApachePdfBoxDocumentParser());
////		} else if (ArrayUtils.contains(POI_DOC_TYPES, ext)) {
////			document = loadDocument(pathName, new ApachePoiDocumentParser());
////		}
//		else {
//			log.warn("该文件类型:{}无法解析，忽略", ext);
//			throw new RuntimeException("该文件类型无法解析");
//		}
//		return document;
//	}
//}
