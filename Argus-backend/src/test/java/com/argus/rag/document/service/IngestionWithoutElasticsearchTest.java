package com.argus.rag.document.service;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.ingestion.mapper.DocumentChunkMapper;
import com.argus.rag.ingestion.service.DocumentIngestionAsyncService;
import com.argus.rag.ingestion.service.DocumentIngestionProcessor;
import com.argus.rag.ingestion.vector.VectorIngestionService;
import com.argus.rag.engine.elasticsearch.ElasticsearchChunkIndexService;
class IngestionWithoutElasticsearchTest {
 @Test void esWriteFailurePreservesReadyAfterSuccessfulVectorProcessing(){
  var documents=mock(DocumentMapper.class);var processor=mock(DocumentIngestionProcessor.class);
  var chunks=mock(DocumentChunkMapper.class);var vectors=mock(VectorIngestionService.class);var es=mock(ElasticsearchChunkIndexService.class);
  var document=new DocumentEntity();document.setId(1L);document.setGroupId(2L);document.setStatus("PROCESSING");document.setFileName("course.md");
  when(documents.selectByIdAndGroupId(1L,2L)).thenReturn(document);
  when(chunks.selectByDocumentId(1L)).thenReturn(java.util.List.of());
  doThrow(new IllegalStateException("offline")).when(es).indexReadyChunks(anyString(),anyList());
  com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
    new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),""),DocumentEntity.class);
  when(documents.update(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.any())).thenReturn(1);
  var service=new DocumentIngestionAsyncService(documents,processor,chunks,vectors,es);
  service.ingestDocument(1L,2L);
  verify(processor).process(1L,2L);
  var capture=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
  verify(documents).update(org.mockito.ArgumentMatchers.isNull(),capture.capture());
  capture.getValue().getSqlSet();
  org.junit.jupiter.api.Assertions.assertTrue(capture.getValue().getParamNameValuePairs().containsValue("READY"));
 }
}
