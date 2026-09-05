package cn.admin.scaffold.module.common;

import java.io.InputStream;

public interface FileStorage {

    String type();

    StoredObject store(byte[] content, String originalName, String contentType, String extension, String category)
            throws Exception;

    InputStream open(String objectKey) throws Exception;

    void delete(String objectKey) throws Exception;

    /**
     * 生成对象直传的预签名 PUT URL（前端绕过应用服务器直传对象存储）。
     * 存储后端不支持时返回 null，调用方回退普通上传。
     */
    default String presignedUpload(String objectKey, String contentType, long size) throws Exception {
        return null;
    }

    /**
     * 生成对象下载的预签名 GET URL（临时分享/浏览器直链）。
     * 存储后端不支持时返回 null，调用方回退应用内下载接口。
     */
    default String presignedDownload(String objectKey) throws Exception {
        return null;
    }
}
