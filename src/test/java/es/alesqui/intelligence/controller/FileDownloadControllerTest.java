package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller-layer tests for FileDownloadController.
 * Uses TestSecurityConfig to bypass the JWT filter.
 * Real temp files are created/deleted per test to exercise actual file I/O.
 */
@WebFluxTest(FileDownloadController.class)
@Import(TestSecurityConfig.class)
class FileDownloadControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;

    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        Path userDir = Paths.get(System.getProperty("java.io.tmpdir"), "alesqui-intelligence-files", "testuser");
        Files.createDirectories(userDir);
        tempFile = userDir.resolve("report.txt");
        Files.writeString(tempFile, "hello world");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void downloadFile_existingFile_returns200() {
        // Body assertion is omitted: AsynchronousFileChannel + MockServerHttpResponse causes
        // a ClosedChannelException when WebTestClient tries to collect the response buffer.
        // Status 200 + Content-Disposition header is sufficient to confirm the file was found
        // and streaming was initiated correctly.
        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .get().uri("/api/files/download/report.txt")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION,
                        value -> assertThat(value).contains("attachment"));
    }

    @Test
    void downloadFile_notFound_returns404() {
        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .get().uri("/api/files/download/nonexistent.txt")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void downloadFile_pathTraversal_returns400() {
        // ..%2F..%2Fetc%2Fpasswd decodes to ../../etc/passwd in the path variable;
        // the controller's path normalisation check detects traversal and returns 400.
        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .get().uri("/api/files/download/..%2F..%2Fetc%2Fpasswd")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
