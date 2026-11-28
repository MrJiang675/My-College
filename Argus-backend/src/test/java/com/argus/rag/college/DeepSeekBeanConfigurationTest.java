package com.argus.rag.college;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.ai.model.openai.autoconfigure.*;
import org.springframework.ai.model.zhipuai.autoconfigure.*;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import com.argus.rag.assistant.support.config.*;
import static org.assertj.core.api.Assertions.assertThat;
class DeepSeekBeanConfigurationTest {
 @Test void onlyDeepSeekChatAndExisting1024DimensionalEmbeddingAreSelected(){
  new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class,OpenAiEmbeddingAutoConfiguration.class,ZhiPuAiChatAutoConfiguration.class,ZhiPuAiEmbeddingAutoConfiguration.class,SpringAiRetryAutoConfiguration.class,ToolCallingAutoConfiguration.class))
   .withUserConfiguration(AssistantChatModelConfiguration.class,DeepSeekApiConfiguration.class)
   .withBean(DeepSeekProperties.class,DeepSeekProperties::new).withBean(com.fasterxml.jackson.databind.ObjectMapper.class,com.fasterxml.jackson.databind.ObjectMapper::new)
   .withPropertyValues("spring.ai.model.chat=openai","spring.ai.model.embedding=zhipuai","spring.ai.openai.api-key=test-key","spring.ai.openai.chat.options.model=deepseek-flash","spring.ai.openai.embedding.enabled=false","spring.ai.zhipuai.api-key=test-key","spring.ai.zhipuai.chat.enabled=false","spring.ai.zhipuai.embedding.options.model=embedding-2","spring.ai.zhipuai.embedding.options.dimensions=1024")
   .run(ctx->{assertThat(ctx).hasNotFailed();assertThat(ctx.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);assertThat(ctx.getBean(ChatModel.class)).isSameAs(ctx.getBean("openAiChatModel"));assertThat(ctx.getBeansOfType(ChatModel.class).values().stream().distinct().count()).isEqualTo(1);assertThat(ctx.getBean(EmbeddingModel.class)).isSameAs(ctx.getBean("zhiPuAiEmbeddingModel"));assertThat(ctx.getBeansOfType(EmbeddingModel.class).values().stream().distinct().count()).isEqualTo(1);assertThat(ctx.getBean(OpenAiChatModel.class).getDefaultOptions().getModel()).isEqualTo("deepseek-flash");});
 }
}
