package cn.admin.scaffold.module.common;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.scan.enabled", havingValue = "true")
public class ClamAvFileVirusScanner implements FileVirusScanner {

    private static final int CHUNK_SIZE = 64 * 1024;

    private final String host;
    private final int port;
    private final int timeoutSeconds;
    private final boolean failOpen;

    public ClamAvFileVirusScanner(
            @Value("${app.storage.scan.clamav.host:localhost}") String host,
            @Value("${app.storage.scan.clamav.port:3310}") int port,
            @Value("${app.storage.scan.clamav.timeout-seconds:10}") int timeoutSeconds,
            @Value("${app.storage.scan.fail-open:false}") boolean failOpen) {
        this.host = host;
        this.port = port;
        this.timeoutSeconds = timeoutSeconds;
        this.failOpen = failOpen;
    }

    @Override
    public ScanResult scan(byte[] content, String originalName, String extension) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutSeconds * 1000);
            socket.setSoTimeout(timeoutSeconds * 1000);
            sendPayload(socket, content);
            String response = readResponse(socket);
            if (response.contains("FOUND") || response.contains("ERROR")) {
                return ScanResult.blocked(response);
            }
            if (response.contains("OK")) {
                return ScanResult.ok();
            }
            log.warn("Unexpected ClamAV response: {}", response);
            return ScanResult.blocked("病毒扫描响应异常");
        } catch (IOException exception) {
            log.error("ClamAV scan failed, host={}:{}", host, port, exception);
            if (failOpen) {
                log.warn("ClamAV unavailable, upload allowed by fail-open configuration");
                return new ScanResult(true, "病毒扫描服务不可用，按配置放行");
            }
            throw new BusinessException(ResultCode.FILE_SCAN_ERROR);
        }
    }

    private void sendPayload(Socket socket, byte[] content) throws IOException {
        OutputStream output = socket.getOutputStream();
        output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
        for (int offset = 0; offset < content.length; offset += CHUNK_SIZE) {
            int length = Math.min(CHUNK_SIZE, content.length - offset);
            writeInt(output, length);
            output.write(content, offset, length);
        }
        writeInt(output, 0);
        output.flush();
    }

    private String readResponse(Socket socket) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try (InputStream input = socket.getInputStream()) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                response.write(buffer, 0, read);
                if (response.toString(StandardCharsets.US_ASCII).contains("\n")) {
                    break;
                }
            }
        }
        return response.toString(StandardCharsets.US_ASCII).trim();
    }

    private void writeInt(OutputStream output, int value) throws IOException {
        output.write((value >>> 24) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }
}
