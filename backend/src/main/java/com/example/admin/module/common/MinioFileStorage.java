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
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioFileStorage implements FileStorage {

    @Value("${app.storage.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${app.storage.minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${app.storage.minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${app.storage.minio.bucket:admin}")
    private String bucket;

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

