package com.argus.rag.college;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.ingestion.service.pipeline.parser.DocumentParserFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;

class CollegeFileExtractControllerTest {
 private CollegeFileExtractController controller(String base){return new CollegeFileExtractController(mock(CurrentUserService.class),new DocumentParserFactory(),"local-fixture",base,"configured-vision-model",5,2);}
 @Test void extractsAndTruncatesShortText(){
  var result=controller("http://127.0.0.1:1").extract(new MockMultipartFile("file","notes.txt","text/plain","学".repeat(31000).getBytes(StandardCharsets.UTF_8))).data();
  assertEquals(30000,result.charCount());assertTrue(result.truncated());assertEquals("document-parser",result.model());
 }
 @Test void rejectsOversizeImageBeforeCallingVision(){
  var file=new MockMultipartFile("file","big.png","image/png",new byte[]{1}){public long getSize(){return 6L*1024*1024;}};
  assertTrue(assertThrows(BusinessException.class,()->controller("http://127.0.0.1:1").extract(file)).getMessage().contains("5 MB"));
 }
 @Test void sendsConfiguredMultimodalRequestToMockServer()throws Exception {
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  var request=new java.util.concurrent.atomic.AtomicReference<String>();
  server.createContext("/v4/chat/completions",exchange->{request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] body="{\"choices\":[{\"message\":{\"content\":\"图中文字【字迹模糊需核实】\"}}]}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  try{
   var result=controller("http://127.0.0.1:"+server.getAddress().getPort()).extract(new MockMultipartFile("file","a.png","image/png",new byte[]{1,2,3})).data();
   assertTrue(result.extractedText().contains("图中文字"));assertTrue(request.get().contains("configured-vision-model"));assertTrue(request.get().contains("image_url"));
  }finally{server.stop(0);}
 }
 @Test void blankScannedPdfHasReadableError()throws Exception{
  var bytes=new java.io.ByteArrayOutputStream();try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()){pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(bytes);}
  assertTrue(assertThrows(BusinessException.class,()->controller("http://127.0.0.1:1").extract(new MockMultipartFile("file","scan.pdf","application/pdf",bytes.toByteArray()))).getMessage().contains("截图"));
 }
}
