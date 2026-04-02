package com.lantu.connect.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.storage.FileStorageSupport;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.SkillPackChunkInitRequest;
import com.lantu.connect.gateway.dto.SkillPackChunkInitResponse;
import com.lantu.connect.gateway.dto.SkillPackChunkStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillPackChunkedUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void initPutCompleteCallsUploadPackBytesWithMergedPayload() {
        FileStorageSupport fss = mock(FileStorageSupport.class);
        when(fss.getUploadDir()).thenAnswer(inv -> tempDir.toString());
        ObjectMapper om = new ObjectMapper();
        SkillPackUploadService upload = mock(SkillPackUploadService.class);
        when(upload.uploadPackBytes(anyLong(), any(), anyString(), any(), any())).thenReturn(
                ResourceManageVO.builder().id(42L).resourceType("skill").resourceCode("c").displayName("n").status("draft").build());

        SkillPackChunkedUploadService svc = new SkillPackChunkedUploadService(fss, om, upload);

        SkillPackChunkInitRequest req = new SkillPackChunkInitRequest();
        req.setFileName("test.bin");
        req.setFileSize(10);
        SkillPackChunkInitResponse init = svc.init(99L, req);
        assertEquals(10, init.getFileSize());
        assertEquals(1, init.getTotalChunks());

        svc.putChunk(99L, init.getUploadId(), 0, new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });

        SkillPackChunkStatusResponse st = svc.status(99L, init.getUploadId());
        assertEquals(1, st.getReceivedCount());
        assertEquals(1, st.getReceivedChunkIndices().size());
        assertEquals(0, st.getReceivedChunkIndices().get(0));

        svc.complete(99L, init.getUploadId());
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(upload).uploadPackBytes(eq(99L), payload.capture(), eq("test.bin"), isNull(), isNull());
        assertArrayEquals(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, payload.getValue());

        // session cleaned
        Path sessionRoot = tempDir.resolve(".skill-chunk-sessions").resolve(init.getUploadId());
        assertTrue(!java.nio.file.Files.isDirectory(sessionRoot) || !java.nio.file.Files.exists(sessionRoot.resolve("meta.json")));
    }
}
