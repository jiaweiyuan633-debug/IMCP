package cn.admin.scaffold.module.common;

import cn.admin.scaffold.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClamAvFileVirusScannerTest {

    @Test
    void scanAcceptsCleanStream() throws Exception {
        try (FakeClamServer server = FakeClamServer.start("stream: OK\n")) {
            ClamAvFileVirusScanner scanner = new ClamAvFileVirusScanner("127.0.0.1", server.port(), 5, false);
            FileVirusScanner.ScanResult result = scanner.scan(new byte[]{1, 2, 3}, "a.txt", "txt");
            assertTrue(result.clean());
        }
    }

    @Test
    void scanBlocksFoundVirus() throws Exception {
        try (FakeClamServer server = FakeClamServer.start("stream: Eicar-Test-Signature FOUND\n")) {
            ClamAvFileVirusScanner scanner = new ClamAvFileVirusScanner("127.0.0.1", server.port(), 5, false);
            FileVirusScanner.ScanResult result = scanner.scan(new byte[]{1, 2, 3}, "a.txt", "txt");
            assertFalse(result.clean());
            assertTrue(result.message().contains("FOUND"));
        }
    }

    @Test
    void scanFailsClosedWhenServiceUnavailable() {
        ClamAvFileVirusScanner scanner = new ClamAvFileVirusScanner("127.0.0.1", 1, 1, false);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> scanner.scan(new byte[]{1}, "a.txt", "txt"));
        assertEquals(1027, exception.getCode());
    }

    @Test
    void scanFailsOpenWhenServiceUnavailable() {
        ClamAvFileVirusScanner scanner = new ClamAvFileVirusScanner("127.0.0.1", 1, 1, true);
        FileVirusScanner.ScanResult result = scanner.scan(new byte[]{1}, "a.txt", "txt");
        assertTrue(result.clean());
        assertTrue(result.message().contains("放行"));
    }

    private static final class FakeClamServer implements AutoCloseable {

        private final ServerSocket serverSocket;

        private FakeClamServer(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        static FakeClamServer start(String response) throws IOException {
            ServerSocket socket = new ServerSocket(0);
            CompletableFuture.runAsync(() -> {
                try (Socket client = socket.accept();
                     InputStream input = client.getInputStream();
                     OutputStream output = client.getOutputStream()) {
                    readCommand(input);
                    readChunks(input);
                    output.write(response.getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                } catch (IOException ignored) {
                    // test server closed
                }
            });
            return new FakeClamServer(socket);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private static void readCommand(InputStream input) throws IOException {
            byte[] command = new byte[10];
            int read = 0;
            while (read < command.length) {
                int count = input.read(command, read, command.length - read);
                if (count == -1) {
                    break;
                }
                read += count;
            }
        }

        private static void readChunks(InputStream input) throws IOException {
            while (true) {
                int first = input.read();
                int second = input.read();
                int third = input.read();
                int fourth = input.read();
                if (first == -1 || second == -1 || third == -1 || fourth == -1) {
                    return;
                }
                int length = (first << 24) | (second << 16) | (third << 8) | fourth;
                if (length == 0) {
                    return;
                }
                long skipped = input.skip(length);
                while (skipped < length) {
                    long more = input.skip(length - skipped);
                    if (more <= 0) {
                        return;
                    }
                    skipped += more;
                }
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
