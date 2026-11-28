package com.argus.rag.college;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.exception.*;
import com.argus.rag.college.DeepSeekSimulationController.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class DeepSeekSimulationControllerTest {
 private final ObjectMapper json=new ObjectMapper();
 private PracticeSimulateRequest request(){return new PracticeSimulateRequest("光学折射","可调折射率",null,null,"fast",null);}
 @Test void unauthenticatedAndEmptyInputAreRejected()throws Exception{
  var users=mock(CurrentUserService.class);var service=new DeepSeekSimulationService(new DeepSeekProperties(),json);var mvc=MockMvcBuilders.standaloneSetup(new DeepSeekSimulationController(users,service)).setControllerAdvice(new GlobalExceptionHandler()).build();
  when(users.getRequiredCurrentUser()).thenThrow(new UnauthorizedException("未登录"));mvc.perform(post("/api/college/practice/simulate-stream").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());reset(users);
  mvc.perform(post("/api/college/practice/simulate-stream").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());assertEquals(3,service.availablePermits());
 }
 @Test void missingKeyIsReadableSseNot500()throws Exception{var service=new DeepSeekSimulationService(new DeepSeekProperties(),json);var events=service.simulate(request()).collectList().block();assertEquals("error",events.getFirst().event());assertTrue(events.getFirst().data().get("message").toString().contains("DEEPSEEK_API_KEY"));assertEquals(3,service.availablePermits());}
 @Test void fencesAreRemovedAndPolicyPrecedesEveryScript(){String raw="```html\n<!DOCTYPE html><html><head><meta http-equiv='refresh' content='0;url=https://evil.invalid'><script src='https://evil.invalid/x'></script></head><body><canvas id='c'></canvas><script>throw Error('test')</script></body></html>\n```\n### 额外解释";String safe=SimulationHtmlSupport.sanitizeAndWrapHtml(raw);assertTrue(safe.startsWith("<!DOCTYPE html>"));assertFalse(safe.contains("```"));assertFalse(safe.contains("额外解释"));assertFalse(safe.contains("script src"));assertFalse(safe.contains("http-equiv='refresh'"));assertTrue(safe.indexOf("Content-Security-Policy")<safe.indexOf("<script>"));assertTrue(safe.contains("connect-src 'none'"));assertTrue(safe.contains("unhandledrejection"));assertTrue(safe.contains("argus-sim-error"));}
 @Test void imageIsNativeMultimodalAndNotAcceptedAsAnArbitraryUrl()throws Exception{var service=new DeepSeekSimulationService(new DeepSeekProperties(),json);assertThrows(BusinessException.class,()->service.validate(new PracticeSimulateRequest(null,"",null,"https://example.com/a.png","fast",null)));assertThrows(BusinessException.class,()->service.validate(new PracticeSimulateRequest(null,"",null,"data:image/png;base64,AAAA","fast",null)));var body=service.payload("goal","data:image/png;base64,fixture","deep");assertEquals("high",body.get("reasoning_effort"));assertTrue(json.writeValueAsString(body).contains("image_url"));assertTrue(json.writeValueAsString(body).contains("goal"));}
 @Test void cancellationCompletionErrorAndRepairAllReleasePermit()throws Exception{
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());var count=new java.util.concurrent.atomic.AtomicInteger();var seen=new java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
  server.createContext("/chat/completions",x->{try{seen.set(json.readTree(x.getRequestBody()));int call=count.incrementAndGet();x.getResponseHeaders().set("Content-Type","text/event-stream");x.sendResponseHeaders(200,0);var out=x.getResponseBody();if(call==1){out.write("data: {\"choices\":[{\"delta\":{\"content\":\"<canvas>\"}}]}\n\n".getBytes());out.flush();Thread.sleep(2000);}else if(call==2||call==4){out.write("data: {\"choices\":[{\"delta\":{\"content\":\"<!DOCTYPE html><canvas></canvas><script>1+1</script>\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".getBytes());}else {out.write("data: {\"error\":{\"message\":\"upstream fixture failure\"}}\n\n".getBytes());}out.close();}catch(Exception ignored){}finally{x.close();}});server.start();
  try{var p=new DeepSeekProperties();p.setApiKey("fixture");p.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());p.setSimulationMaxConcurrent(1);var service=new DeepSeekSimulationService(p,json);
   var first=service.simulate(request()).filter(e->"delta".equals(e.event())).next().block(Duration.ofSeconds(8));assertNotNull(first);awaitRelease(service);
   var result=service.simulate(request()).filter(e->"done".equals(e.event())).next().block(Duration.ofSeconds(8));assertNotNull(result);awaitRelease(service);assertEquals("low",seen.get().path("reasoning_effort").asText());
   var error=service.simulate(request()).filter(e->"error".equals(e.event())).next().block(Duration.ofSeconds(8));assertNotNull(error);awaitRelease(service);
   var repaired=service.repair(new PracticeRepairRequest("test","goal","<canvas></canvas>","error line 1")).block(Duration.ofSeconds(8));assertNotNull(repaired);assertTrue(repaired.html().contains("Content-Security-Policy"));awaitRelease(service);
   assertThrows(BusinessException.class,()->service.repair(new PracticeRepairRequest("test","goal","<canvas></canvas>","error line 1")).block(Duration.ofSeconds(8)));awaitRelease(service);
  }finally{server.stop(0);}
 }
 @Test void upstreamReadTimeoutReleasesPermit()throws Exception{
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());server.createContext("/chat/completions",x->{try{x.getRequestBody().readAllBytes();x.getResponseHeaders().set("Content-Type","text/event-stream");x.sendResponseHeaders(200,0);x.getResponseBody().flush();Thread.sleep(6500);}catch(Exception ignored){}finally{x.close();}});server.start();
  try{var p=new DeepSeekProperties();p.setApiKey("fixture");p.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());p.setSimulationMaxConcurrent(1);p.setReadTimeoutSeconds(5);var service=new DeepSeekSimulationService(p,json);var error=service.simulate(request()).filter(e->"error".equals(e.event())).next().block(Duration.ofSeconds(8));assertNotNull(error);awaitRelease(service);}finally{server.stop(0);}
 }
 private void awaitRelease(DeepSeekSimulationService s)throws Exception{for(int i=0;i<100&&s.availablePermits()!=1;i++)Thread.sleep(10);assertEquals(1,s.availablePermits());}
}
