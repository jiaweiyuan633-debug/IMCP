package cn.admin.scaffold.module.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.common.vo.UploadResponse;
import cn.admin.scaffold.module.system.entity.SysFileDO;
import cn.admin.scaffold.module.system.mapper.SysFileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    private FileVirusScanner scanner;
    private FileUploadProperties properties;
    private FileStorageManager manager;

    @BeforeEach
    void setUp() {
        storage = mock(FileStorage.class);
        fileMapper = mock(SysFileMapper.class);
        quotaService = mock(StorageQuotaService.class);
        scanner = mock(FileVirusScanner.class);
        properties = new FileUploadProperties();
        manager = new FileStorageManager(storage, fileMapper, quotaService, scanner, properties);
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
        doThrow(new BusinessException(cn.admin.scaffold.common.ResultCode.STORAGE_LIMIT_EXCEEDED))
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

    @Test
    void storeBytesReusesPipelineForChunkedMerge() throws Exception {
        when(scanner.scan(any(), any(), any())).thenReturn(FileVirusScanner.ScanResult.ok());
        when(storage.store(any(), any(), any(), any(), any()))
                .thenReturn(new StoredObject("2026/08/09/merged.png", "local", null));
        when(fileMapper.insert(any(SysFileDO.class))).thenAnswer(invocation -> {
            SysFileDO entity = invocation.getArgument(0);
            entity.setId(2L);
            return 1;
        });

        UploadResponse response = manager.storeBytes(PNG_CONTENT, "merged.png", "image/png", "image", "png");

        assertEquals("/files/2", response.getUrl());
        verify(storage).store(eq(PNG_CONTENT), eq("merged.png"), eq("image/png"), eq("png"), eq("image"));
        verify(fileMapper).updateById(any(SysFileDO.class));
    }

    @Test
    void registerObjectReadsBackAndPersistsWithoutReUploading() throws Exception {
        when(storage.open("1/x.png")).thenReturn(new ByteArrayInputStream(PNG_CONTENT));
        when(scanner.scan(any(), any(), any())).thenReturn(FileVirusScanner.ScanResult.ok());
        when(fileMapper.insert(any(SysFileDO.class))).thenAnswer(invocation -> {
            SysFileDO entity = invocation.getArgument(0);
            entity.setId(3L);
            return 1;
        });

        UploadResponse response = manager.registerObject("1/x.png", "x.png", "image/png", "image");

        assertEquals("/files/3", response.getUrl());
        // 预签名直传：对象已存在，不得再次存储
        verify(storage, never()).store(any(), any(), any(), any(), any());
        verify(fileMapper).updateById(any(SysFileDO.class));
    }

    @Test
    void registerObjectRejectsOversizeObjectWithBoundedRead() throws Exception {
        // 收紧上限到 1MB，超限内容仅 1MB+1 字节，测试内存开销可控
        properties.setMaxSizeMb(1);
        byte[] oversize = new byte[1024 * 1024 + 1];
        when(storage.open("1/x.png")).thenReturn(new ByteArrayInputStream(oversize));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> manager.registerObject("1/x.png", "x.png", "image/png", "image"));

        assertEquals(1001, exception.getCode());
        // 超限对象不滞留存储；不得落入入库/扫描管线
        verify(storage).delete("1/x.png");
        verify(fileMapper, never()).insert(any(SysFileDO.class));
    }

    @Test
    void registerObjectDeletesInfectedObject() throws Exception {
        when(storage.open("1/x.png")).thenReturn(new ByteArrayInputStream(PNG_CONTENT));
        when(scanner.scan(any(), any(), any())).thenReturn(FileVirusScanner.ScanResult.blocked("Eicar FOUND"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> manager.registerObject("1/x.png", "x.png", "image/png", "image"));

        assertEquals(1026, exception.getCode());
        // 中毒文件不滞留存储
        verify(storage).delete("1/x.png");
        verify(fileMapper, never()).insert(any(SysFileDO.class));
    }

    // ---------- R4-1.43：历史 /uploads/{objectKey} 归属校验（file-token 签发前防跨租户） ----------

    /** URL 精确匹配且租户一致：返回文件，允许签发令牌。 */
    @Test
    void legacyUrlOwnershipResolvesFileOfCurrentTenant() {
        SysFileDO file = new SysFileDO();
        file.setId(5L);
        file.setTenantId(1L);
        file.setUrl("/uploads/2026/01/01/abc.png");
        when(fileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(file);

        SysFileDO resolved = manager.getOwnedByLegacyUrlOrThrow("/uploads/2026/01/01/abc.png");

        assertNotNull(resolved);
        assertEquals(5L, resolved.getId());
    }

    /** URL 无匹配记录：拒绝签发（404），不泄露对象是否存在。 */
    @Test
    void legacyUrlOwnershipRejectsMissingRecord() {
        when(fileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> manager.getOwnedByLegacyUrlOrThrow("/uploads/2026/01/01/ghost.png"));
        assertEquals(cn.admin.scaffold.common.ResultCode.DATA_NOT_FOUND.getCode(), exception.getCode());
    }

    /** 记录存在但属于其他租户：拒绝签发，防跨租户读历史文件。 */
    @Test
    void legacyUrlOwnershipRejectsCrossTenant() {
        SysFileDO otherTenant = new SysFileDO();
        otherTenant.setId(9L);
        otherTenant.setTenantId(2L);
        when(fileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(otherTenant);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> manager.getOwnedByLegacyUrlOrThrow("/uploads/2026/01/01/abc.png"));
        assertEquals(cn.admin.scaffold.common.ResultCode.DATA_NOT_FOUND.getCode(), exception.getCode());
    }
}
