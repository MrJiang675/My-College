package com.argus.rag.document.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.enums.SystemRole;
import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.document.mapper.DocumentUploadChunkMapper;
import com.argus.rag.document.mapper.DocumentUploadSessionMapper;
import com.argus.rag.document.model.dto.UploadDocumentRequest;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.engine.elasticsearch.ElasticsearchChunkIndexService;
import com.argus.rag.engine.storage.ObjectStorageService;
import com.argus.rag.group.service.GroupMembershipService;
import com.argus.rag.ingestion.vector.VectorIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("文档上传服务")
class DocumentUploadServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentUploadSessionMapper documentUploadSessionMapper;

    @Mock
    private DocumentUploadChunkMapper documentUploadChunkMapper;

    @Mock
    private GroupMembershipService groupMembershipService;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private VectorIngestionService vectorIngestionService;

    @Mock
    private ElasticsearchChunkIndexService elasticsearchChunkIndexService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;


    @Test
    void oversizedUploadRejectedBeforeSessionWrite() {
        var service = new DocumentUploadService(documentMapper,documentUploadSessionMapper,documentUploadChunkMapper,
                groupMembershipService,objectStorageService,vectorIngestionService,elasticsearchChunkIndexService,applicationEventPublisher);
        var request = new com.argus.rag.document.model.dto.UploadInitRequest(1L,"large.pdf",51L*1024*1024,
                "application/pdf","a".repeat(64),5L*1024*1024,3);
        var failure = org.junit.jupiter.api.Assertions.assertThrows(com.argus.rag.common.exception.BusinessException.class,
                () -> service.initUpload(null,request));
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("50"));
        org.mockito.Mockito.verifyNoInteractions(documentUploadSessionMapper,objectStorageService);
    }

    @Test
    @DisplayName("直接上传文档时应计算并保存文件 SHA-256")
    void shouldCalculateFileHashForDirectUpload() {
        byte[] content = "hello argus rag".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.md",
                "text/markdown",
                content
        );
        UploadDocumentRequest request = new UploadDocumentRequest();
        request.setGroupId(1L);
        request.setFile(file);
        when(objectStorageService.getDefaultBucket()).thenReturn("argus-rag-documents");
        when(groupMembershipService.requireGroupOwner(1L))
                .thenReturn(new CurrentUserService.CurrentUser(1L, "admin", "ADMIN", SystemRole.ADMIN, false, true));
        when(documentMapper.insert(any(DocumentEntity.class))).thenAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            document.setId(99L);
            return 1;
        });

        DocumentUploadService service = new DocumentUploadService(
                documentMapper,
                documentUploadSessionMapper,
                documentUploadChunkMapper,
                groupMembershipService,
                objectStorageService,
                vectorIngestionService,
                elasticsearchChunkIndexService,
                applicationEventPublisher
        );

        Long documentId = service.uploadDocument(1L, request);

        assertThat(documentId).isEqualTo(99L);
        ArgumentCaptor<DocumentEntity> documentCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentMapper).insert(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getFileHash())
                .isEqualTo("31b9498ab69cdd12c68dc4261bf8d2327b49cb7a1f99ecc6b61eb394c4e29ca0");
    }
    @Test void smallTransportChunksMergeOnDiskAndValidateFullHash() throws Exception {
        var service = new DocumentUploadService(documentMapper,documentUploadSessionMapper,documentUploadChunkMapper,
            groupMembershipService,objectStorageService,vectorIngestionService,elasticsearchChunkIndexService,applicationEventPublisher);
        var session=new com.argus.rag.document.model.entity.DocumentUploadSessionEntity();
        session.setStorageBucket("test");session.setFileSize(6L);session.setContentType("text/plain");
        session.setFileHash(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("abcdef".getBytes())));
        var a=new com.argus.rag.document.model.entity.DocumentUploadChunkEntity();a.setStorageObjectKey("a");
        var b=new com.argus.rag.document.model.entity.DocumentUploadChunkEntity();b.setStorageObjectKey("b");
        when(objectStorageService.getObject("test","a")).thenReturn(new java.io.ByteArrayInputStream("abc".getBytes()));
        when(objectStorageService.getObject("test","b")).thenReturn(new java.io.ByteArrayInputStream("def".getBytes()));
        org.mockito.Mockito.doAnswer(call->{assertThat(new String(((java.io.InputStream)call.getArgument(2)).readAllBytes())).isEqualTo("abcdef");return null;})
            .when(objectStorageService).putObject(org.mockito.ArgumentMatchers.eq("test"),org.mockito.ArgumentMatchers.eq("final"),any(java.io.InputStream.class),org.mockito.ArgumentMatchers.eq(6L),org.mockito.ArgumentMatchers.eq("text/plain"));
        service.mergeStoredChunks(session,java.util.List.of(a,b),"final");
        verify(objectStorageService).putObject(org.mockito.ArgumentMatchers.eq("test"),org.mockito.ArgumentMatchers.eq("final"),any(java.io.InputStream.class),org.mockito.ArgumentMatchers.eq(6L),org.mockito.ArgumentMatchers.eq("text/plain"));
    }
}
