package com.lantu.connect.common.service;

import com.lantu.connect.common.config.FileBootstrapProperties;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.storage.FileStorageSupport;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        RuntimeAppConfigService runtime = mock(RuntimeAppConfigService.class);
        FileBootstrapProperties fileProps = new FileBootstrapProperties();
        fileProps.setUploadDir(tempDir.toString());
        fileProps.setMaxSizeMb(10);
        fileProps.setAllowedCategories("document,avatar,image,attachment,temp,dataset");
        when(runtime.file()).thenReturn(fileProps);

        FileStorageSupport support = new FileStorageSupport(runtime);
        service = new FileStorageService(support, runtime);
    }

    @Test
    void rejectsTraversalCategory() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.json", "application/json", "{\"x\":1}".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.store(file, "../../etc"));
        assertTrue(ex.getMessage().contains("非法文件分类"));
    }

    @Test
    void rejectsUnknownCategory() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.json", "application/json", "{\"x\":1}".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.store(file, "custom"));
        assertTrue(ex.getMessage().contains("不允许的文件分类"));
    }

    @Test
    void storesInsideUploadRoot() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.json", "application/json", "{\"x\":1}".getBytes());
        String url = service.store(file, "document");
        assertTrue(url.startsWith("/uploads/document/"));
    }
}
