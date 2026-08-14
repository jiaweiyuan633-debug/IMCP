package com.example.admin.module.common;

import cn.hutool.core.util.IdUtil;
import com.example.admin.common.TenantContext;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioFileStorage implements FileStorage {

    /** MinIO 官方镜像默认凭据；生产使用即等于对桶开放全权访问，构造时 fail-fast 拒绝。 */
    private static final String DEFAULT_ACCESS_KEY = "minioadmin";
    private static final String DEFAULT_SECRET_KEY = "minioadmin";

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;

    public MinioFileStorage(
            @Value("${app.storage.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.storage.minio.access-key:minioadmin}") String accessKey,
            @Value("${app.storage.minio.secret-key:minioadmin}") String secretKey,
            @Value("${app.storage.minio.bucket:admin}") String bucket,
            Environment environment) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
        // 与 TotpService/JwtProperties 同款 fail-fast 策略：仅 dev 允许默认凭据（本地 MinIO 快速演示），
        // 生产（显式启用 minio 存储而未注入独立密钥）启动即失败，杜绝默认口令桶对外泄露
        boolean isDev = environment != null && environment.acceptsProfiles(Profiles.of("dev"));
        if (!isDev && (DEFAULT_ACCESS_KEY.equals(accessKey) || DEFAULT_SECRET_KEY.equals(secretKey))) {
            throw new IllegalStateException("生产环境禁止使用 MinIO 默认凭据 minioadmin/minioadmin，"
                    + "请通过 MINIO_ACCESS_KEY / MINIO_SECRET_KEY 注入独立密钥");
        }
    }

    @Override
    public String type() {
        return "minio";
    }

    @Override
    public StoredObject store(byte[] content, String originalName, String contentType, String extension,
                              String category) throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = IdUtil.fastSimpleUUID() + (extension.isBlank() ? "" : "." + extension);
        String objectKey = TenantContext.getTenantId() + "/" + datePath + "/" + fileName;
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, content.length, -1)
                    .contentType(contentType)
                    .build());
        }
        return new StoredObject(objectKey, type(), null);
    }

    @Override
    public InputStream open(String objectKey) throws Exception {
        MinioClient client = client();
        return client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
    }

    @Override
    public void delete(String objectKey) throws Exception {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        client().removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
    }

    @Override
    public String presignedUpload(String objectKey, String contentType, long size) throws Exception {
        return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .object(objectKey)
                .expiry(15 * 60)
                .build());
    }

    @Override
    public String presignedDownload(String objectKey) throws Exception {
        return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectKey)
                .expiry(60 * 60)
                .build());
    }

    private MinioClient client() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

