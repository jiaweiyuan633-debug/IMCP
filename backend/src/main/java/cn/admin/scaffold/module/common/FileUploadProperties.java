package cn.admin.scaffold.module.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class FileUploadProperties {

    private long maxSizeMb = 20;
    private String allowedExtensions = "jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,txt,zip";

    /** 换算后的字节上限：普通/分片/预签名各上传管线统一以此为准做大小校验。 */
    public long getMaxSizeBytes() {
        return maxSizeMb * 1024L * 1024L;
    }
}
