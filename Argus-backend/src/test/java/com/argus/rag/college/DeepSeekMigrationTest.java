package com.argus.rag.college;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.tool.function.FunctionToolCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DeepSeekMigrationTest {
 @Test void urlNeverDuplicatesVersion(){var p=new DeepSeekProperties();p.setBaseUrl("https://api.deepseek.com/");assertEquals("https://api.deepseek.com/chat/completions",p.completionUrl());p.setBaseUrl("https://api.deepseek.com/v1/");assertEquals("https://api.deepseek.com/v1/chat/completions",p.completionUrl());}
 @Test void openAiCompatibleToolRoundTripsDisableThinking()throws Exception {
  var requests=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();var j=new ObjectMapper();
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/chat/completions",x->{var body=j.readTree(x.getRequestBody());requests.add(body);String response=requests.size()==1?"{\"id\":\"fixture\",\"model\":\"deepseek-flash-fixture\",\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call1\",\"type\":\"function\",\"function\":{\"name\":\"echo\",\"arguments\":\"{\\\"value\\\":\\\"evidence\\\"}\"}}]}}]}":"{\"id\":\"fixture\",\"model\":\"deepseek-flash-fixture\",\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"verified\"}}]}";byte[] bytes=response.getBytes(java.nio.charset.StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();});server.start();
  try {var options=OpenAiChatOptions.builder().model("deepseek-flash").extraBody(Map.of("thinking",Map.of("type","disabled"))).build();var props=new DeepSeekProperties();props.setApiKey("fixture");props.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());var model=OpenAiChatModel.builder().openAiApi(new com.argus.rag.assistant.support.config.DeepSeekApiConfiguration().openAiApi(props,j)).defaultOptions(options).build();
   var callback=FunctionToolCallback.builder("echo",(java.util.function.Function<Input,String>)(i->i.value())).description("Return verified evidence").inputType(Input.class).build();
   var result=model.call(new Prompt(List.of(new UserMessage("Use echo")),OpenAiChatOptions.builder().toolCallbacks(callback).build()));
   assertEquals("verified",result.getResult().getOutput().getText());assertEquals("deepseek-flash-fixture",result.getMetadata().getModel());assertEquals(2,requests.size());for(var r:requests)assertEquals("disabled",r.path("thinking").path("type").asText());assertTrue(requests.get(1).path("messages").toString().contains("evidence"));
  }finally{server.stop(0);}
 }
 @Test void streamingRequestAlsoForcesNonThinking()throws Exception{
  var j=new ObjectMapper();var received=new java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.JsonNode>();var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/chat/completions",x->{received.set(j.readTree(x.getRequestBody()));String response="data: {\"id\":\"fixture\",\"model\":\"deepseek-flash-fixture\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"verified stream\"},\"finish_reason\":null}]}\n\ndata: [DONE]\n\n";byte[] b=response.getBytes(java.nio.charset.StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","text/event-stream");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.start();
  try{var p=new DeepSeekProperties();p.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());p.setApiKey("fixture");var model=OpenAiChatModel.builder().openAiApi(new com.argus.rag.assistant.support.config.DeepSeekApiConfiguration().openAiApi(p,j)).defaultOptions(OpenAiChatOptions.builder().model("deepseek-flash").build()).build();var text=model.stream(new Prompt("test stream")).map(r->r.getResult().getOutput().getText()).collectList().block(java.time.Duration.ofSeconds(8));assertTrue(String.join("",text).contains("verified stream"));assertTrue(received.get().path("stream").asBoolean());assertEquals("disabled",received.get().path("thinking").path("type").asText());}finally{server.stop(0);}
 }
 record Input(String value){}
}
