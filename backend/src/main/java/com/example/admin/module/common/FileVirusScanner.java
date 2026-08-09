package com.example.admin.module.common;

public interface FileVirusScanner {

    ScanResult scan(byte[] content, String originalName, String extension);

    record ScanResult(boolean clean, String message) {
        public static ScanResult ok() {
            return new ScanResult(true, null);
        }

        public static ScanResult blocked(String message) {
            return new ScanResult(false, message);
        }
    }
}
