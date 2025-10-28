package com.bigbear.aigc.biz.common.config;//package com.bigbear.aigc.ai.common.config;
//

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import cn.hutool.extra.spring.SpringUtil;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

	public static final Executor freeThreadPoolExecutor   = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

	@Bean
	public EmbeddingModel embeddingModel() {
		return OpenAiEmbeddingModel.builder()
//				.apiKey(System.getenv("DASHSCOPE_KEY"))
				.apiKey("asdasatyhdas")
				.modelName("text-embedding-v3")
				.baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .httpClientBuilder(JdkHttpClient.builder())
				.build();

	}


    @Bean
    public EmbeddingModel initEmbeddingModel() {
             return new AllMiniLmL6V2EmbeddingModel();
    }

	@Bean
	public EmbeddingStore<TextSegment> embeddingStore(EmbeddingModel embeddingModel) {
        String property = SpringUtil.getProperty("env.host");

        return PgVectorEmbeddingStore.builder()
				.host(property)
				.port(5432)
				.database("postgres")                        // Required: Database name
				.user("root")                             // Required: Database user
				.password("123456")                     // Required: Database password
				.table("test")                      // Required: Table name to store embeddings
//				.dimension(embeddingModel.dimension()) //todo 测试阶段绕过
				.dimension(1536)


				// Indexing and performance options
//				.useIndex(true)                             // Enable IVFFlat index
//				.indexListSize(100)                         // Number of lists for IVFFlat index
//
//				// Table creation options
//				.createTable(true)                          // Automatically create the table if it doesn’t exist
//				.dropTableFirst(false)                      // Don’t drop the table first (set to true if you want a fresh start)
//
//				// Metadata storage format
//				.metadataStorageConfig(MetadataStorageConfig.combinedJsonb()) // Store metadata as a combined JSONB column

				.build();
	}



	@Bean
	public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore
			, EmbeddingModel embeddingModel) {
		//内容检索器
		return EmbeddingStoreContentRetriever.builder()
				.embeddingStore(embeddingStore)
				.embeddingModel(embeddingModel)
				.maxResults(3)
				// 根据query动态指定maxResults，这里固定返回3
				.dynamicMaxResults(query -> 3)
				.minScore(0.75)
				// 根据query动态指定minScore，这里固定返回0.75
				.dynamicMinScore(query -> 0.75)
				.filter(metadataKey("uuid").isEqualTo(""))
				.build();
	}

	@Bean
	public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever,@Qualifier("myChatModel") ChatModel ChatModel) {
		// 创建检索增强器，用于增强提示生成过程中的内容检索,作为 RAG 流程的入口点
		return DefaultRetrievalAugmentor.builder()
				.contentRetriever(contentRetriever)
				.executor(freeThreadPoolExecutor)
				.queryTransformer(new CompressingQueryTransformer(ChatModel))
				.build();
	}

}
