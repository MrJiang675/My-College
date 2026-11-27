
package com.argus.rag.engine.elasticsearch;
import java.net.http.HttpClient;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OptionalElasticsearchTest {
 @Test void disabledSkipsAllIo(){
  var http=mock(HttpClient.class);
  var service=new ElasticsearchChunkIndexService(new ObjectMapper(),http,"localhost",9,"http","test");
  ReflectionTestUtils.setField(service,"enabled",false);
  service.indexReadyChunks("test",List.of());service.deleteDocumentChunks(1L);
  assertTrue(service.search(1L,"课程",10).isEmpty());verifyNoInteractions(http);
 }
 @Test void unavailableReturnsEmptyAndWriteDoesNotThrow(){
  var service=new ElasticsearchChunkIndexService(new ObjectMapper(),"localhost",1,"http","test");
  assertTrue(service.search(1L,"课程",10).isEmpty());
  assertDoesNotThrow(()->service.indexReadyChunks("test",List.of(new com.argus.rag.ingestion.model.entity.DocumentChunkEntity())));
 }
}
