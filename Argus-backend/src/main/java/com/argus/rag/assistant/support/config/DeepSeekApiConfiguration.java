package com.argus.rag.assistant.support.config;

import com.argus.rag.college.DeepSeekProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.*;
import org.springframework.http.client.*;
import org.springframework.http.client.reactive.*;
import org.springframework.core.io.buffer.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.net.http.HttpClient;

/** Wire-level enforcement also covers nested Spring AI tool calls whose extraBody merging loses defaults. */
@Configuration
public class DeepSeekApiConfiguration {
    public static byte[] nonThinking(byte[] body,ObjectMapper json){try{var n=(ObjectNode)json.readTree(body);n.putObject("thinking").put("type","disabled");return json.writeValueAsBytes(n);}catch(Exception invalid){throw new IllegalArgumentException("DeepSeek 请求体无效");}}
    @Bean
    public OpenAiApi openAiApi(DeepSeekProperties p,ObjectMapper json){
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(1,Math.min(30,p.getConnectTimeoutSeconds())))).build();
        var factory=new JdkClientHttpRequestFactory(client);factory.setReadTimeout(Duration.ofSeconds(p.timeout()));
        var rest=RestClient.builder().requestFactory(factory).requestInterceptor((request,body,execution)->{byte[] updated=nonThinking(body,json);request.getHeaders().setContentLength(updated.length);return execution.execute(request,updated);});
        var connector=new JdkClientHttpConnector(client);connector.setReadTimeout(Duration.ofSeconds(p.timeout()));
        var web=WebClient.builder().clientConnector(connector).codecs(c->c.defaultCodecs().maxInMemorySize(256*1024)).filter((request,next)->{
            var modified=ClientRequest.from(request).body((output,context)->request.body().insert(new ClientHttpRequestDecorator(output){
                @Override public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body){
                    return DataBufferUtils.join(body,4*1024*1024).flatMap(buffer->{byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);DataBufferUtils.release(buffer);byte[] updated=nonThinking(bytes,json);getHeaders().setContentLength(updated.length);return super.writeWith(Mono.just(bufferFactory().wrap(updated)));});
                }
            },context)).build();return next.exchange(modified);
        });
        String root=p.completionUrl().replaceFirst("/chat/completions$","");
        return OpenAiApi.builder().baseUrl(root).completionsPath("/chat/completions").apiKey(p.getApiKey().isBlank()?"test-key":p.getApiKey()).restClientBuilder(rest).webClientBuilder(web).build();
    }
}
