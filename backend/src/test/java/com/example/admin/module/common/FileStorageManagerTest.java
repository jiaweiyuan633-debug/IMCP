package com.example.admin.module.common;

import com.example.admin.common.BusinessException;
import com.example.admin.common.FileAccessService;
import com.example.admin.common.TenantContext;
import com.example.admin.module.common.vo.UploadResponse;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.module.system.mapper.SysFileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageManagerTest {

    private static final byte[] PNG_CONTENT = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0, 0, 0, 0, 0
    };

    private FileStorage storage;
    private SysFileMapper fileMapper;
    private StorageQuotaService quotaService;
    private FileAccessService accessService;
    private FileVirusScanner scanner;
    private FileUploadProperties properties;
    private FileStorageManager manager;

    @BeforeEach
    void setUp() {
        storage = mock(FileStorage.class);
        fileMapper = mock(SysFileMapper.class);
        quotaService = mock(StorageQuotaService.class);
        accessService = mock(FileAccessService.class);
        scanner = mock(FileVirusScanner.class);
        properties = new FileUploadProperties();
        manager = new FileStorageManager(storage, fileMapper, quotaService, accessService, scanner, properties);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void storeRejectsUnsupportedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", new byte[]{'M', 'Z'});
        BusinessException exception = assertThrows(BusinessException.class, () -> manager.store(file, null));
        assertEquals(1001, exception.getCode());
        verify(storage, never()).store(any(), any(), any(), any(), any());
    }

    @Test
    void storeRejectsContentTypeMismatch() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", new byte[]{'M', 'Z', 0, 0});
        BusinessException exception = assertThrows(BusinessException.class, () -> manager.store(file, null));
        assertEquals(1001, exception.getCode());
        verify(storage, never()).store(any(), any(), any(), any(), any());
    }

    @Test
    void storeBlocksInfectedFile() throws Exception {
        when(scanner.scan(any(), any(), any())).thenReturn(FileVirusScanner.ScanResult.blocked("Eicar FOUND"));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_CONTENT);
        BusinessException exception = assertThrows(BusinessException.class, () -> manager.store(file, "image"));
        assertEquals(1026, exception.getCode());
        verify(storage, never()).store(any(), any(), any(), any(), any());
    }

    @Test
    void storeRejectsWhenQuotaExceeded() throws Exception {
        when(scanner.scan(any(), any(), any())).thenReturn(FileVirusScanner.ScanResult.ok());
        doThrow(new BusinessException(com.example.admin.common.ResultCode.STORAGE_LIMIT_EXCEEDED))
                .when(quotaService).check(anyLong());
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_CONTENT);
        assertThrows(BusinessException.class, () -> manager.store(file, null));
        verify(storage, never()).store(any(), any(), any(), any(), any());
    }

    @Test
    void storePersistsMetadataAndReturnsContentUrl() throws Exception {
        when(scanner.scan(any(), any(), any())).thenReturn(FileVirusScanner.ScanResult.ok());
        when(storage.store(any(), any(), any(), any(), any()))
                .thenReturn(new StoredObject("2026/08/09/a.png", "local", "/uploads/2026/08/09/a.png"));
        when(accessService.issue(eq("/files/1"), any())).thenReturn("token");
        when(fileMapper.insert(any(SysFileDO.class))).thenAnswer(invocation -> {
            SysFileDO entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_CONTENT);
        UploadResponse response = manager.store(file, null);

        assertEquals("/files/1", response.getUrl());
        assertEquals("/files/1", response.getContentUrl());
        assertEquals("image", response.getCategory());
        assertEquals("SCANNED", response.getScanStatus());
        assertNotNull(response.getSha256());
        verify(fileMapper).updateById(any(SysFileDO.class));
    }
}
