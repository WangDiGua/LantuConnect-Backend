package com.lantu.connect.common.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.storage.FileStorageSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        FileStorageSupport support = new FileStorageSupport();
        ReflectionTestUtils.setField(support, "uploadDir", tempDir.toString());

        service = new FileStorageService(support);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxSizeMb", 10);
        ReflectionTestUtils.setField(service, "skillPackMaxMb", 10);
        ReflectionTestUtils.setField(service, "storageType", "local");
        ReflectionTestUtils.setField(service, "allowedCategoriesRaw", "document,avatar,image,attachment,temp,dataset");
        service.initAllowedCategories();
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
