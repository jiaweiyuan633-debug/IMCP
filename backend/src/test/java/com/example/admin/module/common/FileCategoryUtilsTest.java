package com.example.admin.module.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileCategoryUtilsTest {

    @Test
    void detectByExtension() {
        assertEquals(FileCategoryUtils.IMAGE, FileCategoryUtils.detect("png", ""));
        assertEquals(FileCategoryUtils.PDF, FileCategoryUtils.detect("pdf", ""));
        assertEquals(FileCategoryUtils.OFFICE, FileCategoryUtils.detect("xlsx", ""));
        assertEquals(FileCategoryUtils.ARCHIVE, FileCategoryUtils.detect("zip", ""));
        assertEquals(FileCategoryUtils.VIDEO, FileCategoryUtils.detect("mp4", ""));
        assertEquals(FileCategoryUtils.OTHER, FileCategoryUtils.detect("bin", ""));
    }

    @Test
    void detectByContentType() {
        assertEquals(FileCategoryUtils.IMAGE, FileCategoryUtils.detect("", "image/webp"));
        assertEquals(FileCategoryUtils.VIDEO, FileCategoryUtils.detect("", "video/mp4"));
        assertEquals(FileCategoryUtils.TEXT, FileCategoryUtils.detect("", "text/plain"));
    }
}
