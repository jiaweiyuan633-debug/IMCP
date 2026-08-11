package com.example.admin.module.common;

import com.example.admin.common.BusinessException;
import com.example.admin.common.TenantContext;
import com.example.admin.module.common.dto.PresignConfirmRequest;
import com.example.admin.module.common.dto.PresignUploadRequest;
import com.example.admin.module.common.vo.PresignUploadResponse;
import com.example.admin.module.common.vo.UploadResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresignedFileServiceTest {

    private FileStorage storage;
    private FileStorageManager fileStorageManager;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private PresignedFileService service;

    @BeforeEach
    void setUp() {
        storage = mock(FileStorage.class);
        fileStorageManager = mock(FileStorageManager.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        service = new PresignedFileService(storage, fileStorageManager, redisTemplate, objectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PresignUploadRequest uploadRequest() {
        PresignUploadRequest request = new PresignUploadRequest();
        request.setFileName("a.png");
        request.setContentType("image/png");
        request.setSize(1024);
        return request;
    }

    @Test
    void createUploadReturnsUnsupportedWhenStorageLacksPresign() throws Exception {
        when(storage.presignedUpload(anyString(), any(), any(long.class))).thenReturn(null);

        PresignUploadResponse response = service.createUpload(uploadRequest());

        assertThat(response.isSupported()).isFalse();
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void createUploadIssuesUrlAndRecordsPending() throws Exception {
        when(storage.presignedUpload(anyString(), any(), any(long.class)))
                .thenReturn("https://minio.example.com/put-url");
        when(storage.type()).thenReturn("minio");

        PresignUploadResponse response = service.createUpload(uploadRequest());

        assertThat(response.isSupported()).isTrue();
        assertThat(response.getUploadUrl()).isEqualTo("https://minio.example.com/put-url");
        assertThat(response.getStorageType()).isEqualTo("minio");
        assertThat(response.getObjectKey()).startsWith("1/");
        verify(valueOps).set(org.mockito.ArgumentMatchers.startsWith("file:presign:"), anyString(),
                eq(Duration.ofMinutes(30)));
    }

    @Test
    void confirmRejectsWhenNoPendingRecord() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.confirm(confirmRequest("1/x.png")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已过期");
    }

    @Test
    void confirmRejectsWhenTenantMismatch() throws Exception {
        String pending = objectMapper.writeValueAsString(Map.of("tenantId", 2L, "userId", 1L));
        when(valueOps.get("file:presign:1/x.png")).thenReturn(pending);

        assertThatThrownBy(() -> service.confirm(confirmRequest("1/x.png")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已过期");
    }

    @Test
    void confirmRegistersObjectAndDeletesPending() throws Exception {
        String pending = objectMapper.writeValueAsString(Map.of("tenantId", 1L, "userId", 1L));
        when(valueOps.get("file:presign:1/x.png")).thenReturn(pending);
        UploadResponse uploadResponse = UploadResponse.builder().build();
        when(fileStorageManager.registerObject("1/x.png", "a.png", "image/png", "image"))
                .thenReturn(uploadResponse);

        UploadResponse result = service.confirm(confirmRequest("1/x.png"));

        assertThat(result).isSameAs(uploadResponse);
        verify(redisTemplate).delete("file:presign:1/x.png");
    }

    private PresignConfirmRequest confirmRequest(String objectKey) {
        PresignConfirmRequest request = new PresignConfirmRequest();
        request.setObjectKey(objectKey);
        request.setFileName("a.png");
        request.setContentType("image/png");
        request.setCategory("image");
        request.setSize(1024);
        return request;
    }
}
