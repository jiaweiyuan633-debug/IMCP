package cn.admin.scaffold.module.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.scan.enabled", havingValue = "false", matchIfMissing = true)
public class NoopFileVirusScanner implements FileVirusScanner {

    @Override
    public ScanResult scan(byte[] content, String originalName, String extension) {
        return ScanResult.ok();
    }
}
