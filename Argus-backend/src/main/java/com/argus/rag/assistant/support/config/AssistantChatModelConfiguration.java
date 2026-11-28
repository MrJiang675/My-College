package com.argus.rag.assistant.support.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AssistantChatModelConfiguration {

    @Bean
    @Primary
    ChatModel primaryChatModel(@Qualifier("openAiChatModel") ChatModel openAiChatModel) {
        return openAiChatModel;
    }

    // 已有 pgvector 为 1024 维：智谱只负责向量化。
    @Bean
    @Primary
    EmbeddingModel primaryEmbeddingModel(@Qualifier("zhiPuAiEmbeddingModel") EmbeddingModel zhiPuAiEmbeddingModel) {
        return zhiPuAiEmbeddingModel;
    }
}
