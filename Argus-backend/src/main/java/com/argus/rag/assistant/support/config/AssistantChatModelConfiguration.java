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
    ChatModel primaryChatModel(@Qualifier("zhiPuAiChatModel") ChatModel zhiPuAiChatModel) {
        return zhiPuAiChatModel;
    }

    // 新增这段代码：把向量化模型也明确指定为智谱
    @Bean
    @Primary
    EmbeddingModel primaryEmbeddingModel(@Qualifier("zhiPuAiEmbeddingModel") EmbeddingModel zhiPuAiEmbeddingModel) {
        return zhiPuAiEmbeddingModel;
    }
}

//package com.argus.rag.assistant.support.config;(注释)
//
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//
///**
// * Assistant 模块 ChatModel 默认配置。
// * <p>
// * 当 classpath 同时存在 DashScope 和 ZhiPuAi 两个 ChatModel 自动配置时，
// * 将智谱 ChatModel 标记为 @Primary，确保无 @Qualifier 的注入默认拿到智谱。
// * </p>
// */
//@Configuration
//public class AssistantChatModelConfiguration {
//
//    @Bean
//    @Primary
//    ChatModel primaryChatModel(@Qualifier("zhiPuAiChatModel") ChatModel zhiPuAiChatModel) {
//        return zhiPuAiChatModel;
//    }
//}
