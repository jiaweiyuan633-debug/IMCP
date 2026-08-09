package com.example.admin.module.common;

import com.example.admin.module.common.vo.UploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorage {

    UploadResponse store(MultipartFile file) throws Exception;
}
