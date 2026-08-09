package com.example.admin.module.common;

import java.io.InputStream;

public interface FileStorage {

    String type();

    StoredObject store(byte[] content, String originalName, String contentType, String extension, String category)
            throws Exception;

    InputStream open(String objectKey) throws Exception;

    void delete(String objectKey) throws Exception;
}
