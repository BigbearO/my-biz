package com.bigbear.aigc.biz.common.config;//package com.bigbear.aigc.ai.common.config;
//
//import com.bigbear.aigc.ai.service.ChatAssistant;
//import dev.langchain4j.data.segment.TextSegment;
//import dev.langchain4j.memory.chat.ChatMemoryProvider;
//import dev.langchain4j.model.chat.ChatModel;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
//import dev.langchain4j.rag.DefaultRetrievalAugmentor;
//import dev.langchain4j.rag.RetrievalAugmentor;
//import dev.langchain4j.rag.content.retriever.ContentRetriever;
//import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
//import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
//import dev.langchain4j.service.AiServices;
//import dev.langchain4j.store.embedding.EmbeddingStore;
//import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.concurrent.Executor;
//import java.util.concurrent.Executors;
//
//import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
//
//@Configuration
//public class RagConfig {
//
//	public static final Executor freeThreadPoolExecutor   = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//
//	@Bean
//	public EmbeddingModel embeddingModel() {
//		return OpenAiEmbeddingModel.builder()
//				.apiKey(System.getenv("DASHSCOPE_KEY"))
//				.modelName("text-embedding-v3")
//				.baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
//				.build();
//	}
//
//	@Bean
//	public EmbeddingStore<TextSegment> embeddingStore(EmbeddingModel embeddingModel) {
//		return PgVectorEmbeddingStore.builder()
//				.host("127.0.0.1")
//				.port(5432)
//				.database("postgres")                        // Required: Database name
//				.user("root")                             // Required: Database user
//				.password("123456")                     // Required: Database password
//				.table("test")                      // Required: Table name to store embeddings
//				.dimension(embeddingModel.dimension())
//
//				// Indexing and performance options
////				.useIndex(true)                             // Enable IVFFlat index
////				.indexListSize(100)                         // Number of lists for IVFFlat index
////
////				// Table creation options
////				.createTable(true)                          // Automatically create the table if it doesn’t exist
////				.dropTableFirst(false)                      // Don’t drop the table first (set to true if you want a fresh start)
////
////				// Metadata storage format
////				.metadataStorageConfig(MetadataStorageConfig.combinedJsonb()) // Store metadata as a combined JSONB column
//
//				.build();
//	}
//
//
//	@Bean
//	// 这个bean也可以使用 @AiService标记接口ChatAssistant来实现
//	public ChatAssistant chatAssistantWithRag(@Qualifier("myChatModel") ChatModel ChatModel,
//											  EmbeddingStore<TextSegment> embeddingStore, ChatMemoryProvider chatMemoryProvider
//	,RetrievalAugmentor retrievalAugmentor) {
//		return AiServices.builder(ChatAssistant.class)
//				.ChatModel(ChatModel)
//				.chatMemoryProvider(chatMemoryProvider)
//				.contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore))
//				.retrievalAugmentor(retrievalAugmentor)
//				.build();
//	}
//
//	@Bean
//	public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore
//			, EmbeddingModel embeddingModel) {
//		//内容检索器
//		return EmbeddingStoreContentRetriever.builder()
//				.embeddingStore(embeddingStore)
//				.embeddingModel(embeddingModel)
//				.maxResults(3)
//				// 根据query动态指定maxResults，这里固定返回3
//				.dynamicMaxResults(query -> 3)
//				.minScore(0.75)
//				// 根据query动态指定minScore，这里固定返回0.75
//				.dynamicMinScore(query -> 0.75)
//				.filter(metadataKey("uuid").isEqualTo(""))
//				.build();
//	}
//
//	@Bean
//	public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever,@Qualifier("myChatModel") ChatModel ChatModel) {
//		// 创建检索增强器，用于增强提示生成过程中的内容检索,作为 RAG 流程的入口点
//		return DefaultRetrievalAugmentor.builder()
//				.contentRetriever(contentRetriever)
//				.executor(freeThreadPoolExecutor)
//				.queryTransformer(new CompressingQueryTransformer(ChatModel))
//				.build();
//	}
//
//}
