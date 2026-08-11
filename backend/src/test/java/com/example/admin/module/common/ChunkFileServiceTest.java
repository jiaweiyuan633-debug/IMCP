package com.example.admin.module.common;

import cn.hutool.crypto.digest.DigestUtil;
import com.example.admin.common.BusinessException;
import com.example.admin.common.DistributedLock;
import com.example.admin.common.TenantContext;
import com.example.admin.module.common.dto.ChunkInitRequest;
import com.example.admin.module.common.vo.ChunkInitResponse;
import com.example.admin.module.common.vo.UploadResponse;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.module.system.mapper.SysFileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkFileServiceTest {

    private static final String UPLOAD_ID = "upload-1";

    @TempDir
    Path tempDir;

    private SysFileMapper fileMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private SetOperations<String, String> setOps;
    private FileStorageManager fileStorageManager;
    private DistributedLock distributedLock;
    private ObjectMapper objectMapper;
    private ChunkFileService service;

    private ChunkFileService.ChunkTask task(int totalChunks, int chunkSize, long totalSize) {
        return new ChunkFileService.ChunkTask(UPLOAD_ID, 1L, "a.txt", "text/plain", "doc",
                totalChunks, chunkSize, totalSize, DigestUtil.sha256Hex("helloworld"), "txt");
    }

    @BeforeEach
    void setUp() throws Exception {
        fileMapper = mock(SysFileMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);
        fileStorageManager = mock(FileStorageManager.class);
        distributedLock = mock(DistributedLock.class);
        objectMapper = new ObjectMapper();
        service = new ChunkFileService(fileMapper, redisTemplate, fileStorageManager, distributedLock, objectMapper);
        ReflectionTestUtils.setField(service, "chunkDirConfig", tempDir.toString());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(distributedLock.execute(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubTask(ChunkFileService.ChunkTask task) throws Exception {
        when(valueOps.get("file:chunk:" + UPLOAD_ID))
                .thenReturn(objectMapper.writeValueAsString(task));
    }

    private ChunkInitRequest initRequest() {
        ChunkInitRequest request = new ChunkInitRequest();
        request.setFileName("a.txt");
        request.setContentType("text/plain");
        request.setCategory("doc");
        request.setTotalChunks(2);
        request.setChunkSize(5);
        request.setTotalSize(10);
        request.setSha256(DigestUtil.sha256Hex("helloworld"));
        return request;
    }

    @Test
    void initReturnsExistingWhenSha256Matches() {
        SysFileDO existing = new SysFileDO();
        existing.setId(5L);
        when(fileMapper.selectOne(any())).thenReturn(existing);

        ChunkInitResponse response = service.init(initRequest());

        assertThat(response.isExists()).isTrue();
        assertThat(response.getFileId()).isEqualTo(5L);
        assertThat(response.getUrl()).isEqualTo("/files/5");
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void initCreatesTaskWhenNoMatch() {
        when(fileMapper.selectOne(any())).thenReturn(null);

        ChunkInitResponse response = service.init(initRequest());

        assertThat(response.isExists()).isFalse();
        assertThat(response.getUploadId()).isNotBlank();
        verify(valueOps).set(startsWith("file:chunk:"), anyString(), eq(Duration.ofHours(2)));
    }

    @Test
    void uploadChunkRejectsOutOfRangeIndex() throws Exception {
        stubTask(task(2, 5, 10));

        assertThatThrownBy(() -> service.uploadChunk(UPLOAD_ID, 5, new MockMultipartFile("c", new byte[1])))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分片序号非法");
    }

    @Test
    void uploadChunkIsIdempotentForReceivedPart() throws Exception {
        stubTask(task(2, 5, 10));
        when(setOps.isMember("file:chunk:" + UPLOAD_ID + ":parts", "0")).thenReturn(true);

        service.uploadChunk(UPLOAD_ID, 0, new MockMultipartFile("c", new byte[5]));

        verify(setOps, never()).add(anyString(), anyString());
    }

    @Test
    void completeRejectsWhenNotAllPartsUploaded() throws Exception {
        stubTask(task(2, 5, 10));
        when(setOps.size("file:chunk:" + UPLOAD_ID + ":parts")).thenReturn(1L);

        assertThatThrownBy(() -> service.complete(UPLOAD_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分片未上传完整");
    }

    @Test
    void completeMergesPartsInOrderAndCleansUp() throws Exception {
        stubTask(task(2, 5, 10));
        when(setOps.size("file:chunk:" + UPLOAD_ID + ":parts")).thenReturn(2L);
        Path chunkDir = tempDir.resolve(UPLOAD_ID);
        Files.createDirectories(chunkDir);
        Files.write(chunkDir.resolve("0"), "hello".getBytes(StandardCharsets.UTF_8));
        Files.write(chunkDir.resolve("1"), "world".getBytes(StandardCharsets.UTF_8));

        UploadResponse response = UploadResponse.builder().build();
        when(fileStorageManager.storeBytes(any(byte[].class), eq("a.txt"), eq("text/plain"), eq("doc"), eq("txt")))
                .thenReturn(response);

        UploadResponse result = service.complete(UPLOAD_ID);

        assertThat(result).isSameAs(response);
        org.mockito.ArgumentCaptor<byte[]> captor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageManager).storeBytes(captor.capture(), eq("a.txt"), eq("text/plain"), eq("doc"), eq("txt"));
        assertThat(new String(captor.getValue(), StandardCharsets.UTF_8)).isEqualTo("helloworld");
        // 任务与分片目录清理
        verify(redisTemplate).delete(List.of("file:chunk:" + UPLOAD_ID, "file:chunk:" + UPLOAD_ID + ":parts"));
        assertThat(Files.exists(chunkDir)).isFalse();
    }

    private String startsWith(String prefix) {
        return org.mockito.ArgumentMatchers.startsWith(prefix);
    }
}
