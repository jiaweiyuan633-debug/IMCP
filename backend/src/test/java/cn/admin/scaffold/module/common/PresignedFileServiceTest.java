package cn.admin.scaffold.module.common;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.common.dto.PresignConfirmRequest;
import cn.admin.scaffold.module.common.dto.PresignUploadRequest;
import cn.admin.scaffold.module.common.vo.PresignUploadResponse;
import cn.admin.scaffold.module.common.vo.UploadResponse;
import cn.admin.scaffold.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresignedFileServiceTest {

    private FileStorage storage;
    private FileStorageManager fileStorageManager;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private FileUploadProperties properties;
    private PresignedFileService service;
    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        // confirm 校验签发用户，测试统一模拟当前登录用户 1
        securityUtils.when(SecurityUtils::tryGetUserId).thenReturn(1L);
        storage = mock(FileStorage.class);
        fileStorageManager = mock(FileStorageManager.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        properties = new FileUploadProperties();
        service = new PresignedFileService(storage, fileStorageManager, redisTemplate, objectMapper, properties);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
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
    void createUploadRejectsOversizeClaimWithoutIssuingUrl() throws Exception {
        PresignUploadRequest request = uploadRequest();
        // 超过默认 20MB 上限的声明必须在签发前被拒绝，连 URL 都不生成
        request.setSize(20L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.createUpload(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小不能超过 20MB");
        verify(storage, never()).presignedUpload(anyString(), any(), any(long.class));
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
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
    void confirmRejectsWhenUserMismatch() throws Exception {
        // 同租户其他用户（签发者 2、当前登录 1）拿到 objectKey 抢先 confirm → 拒绝，
        // 否则可绕过配额与归属登记把对象据为己有
        String pending = objectMapper.writeValueAsString(Map.of("tenantId", 1L, "userId", 2L));
        when(valueOps.get("file:presign:1/x.png")).thenReturn(pending);

        assertThatThrownBy(() -> service.confirm(confirmRequest("1/x.png")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已过期");
        verify(fileStorageManager, never()).registerObject(anyString(), anyString(), anyString(), anyString());
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
