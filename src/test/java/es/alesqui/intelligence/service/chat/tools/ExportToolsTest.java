package es.alesqui.intelligence.service.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("ExportTools")
class ExportToolsTest {

    private ExportTools exportTools;
    private ToolContext toolContext;
    private Path tempDir;

    private static final String EXPORT_DATA = """
            [
                {"id": 1, "name": "Alice", "email": "alice@test.com"},
                {"id": 2, "name": "Bob", "email": "bob@test.com"},
                {"id": 3, "name": "Charlie", "email": "charlie@test.com"}
            ]
            """;

    private static final String TEST_USERNAME = "test_user";

    @BeforeEach
    void setUp() {
        exportTools = new ExportTools(new ObjectMapper());
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(ToolContextKeys.USER_ID, "test-user-id");
        contextMap.put(ToolContextKeys.USERNAME, TEST_USERNAME);
        toolContext = new ToolContext(contextMap);
        tempDir = Path.of(System.getProperty("java.io.tmpdir"), "alesqui-intelligence-files");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                    });
        }
    }

    @Test
    @DisplayName("Should create Excel file and return sandbox link")
    void createExcelFileHappyPath() {
        String result = exportTools.createExcelFile(EXPORT_DATA, "users_export", toolContext);

        assertThat(result).startsWith("File created successfully. Download link: sandbox:/");
        assertThat(result).contains("users_export");
        assertThat(result).endsWith(".xlsx");
    }

    @Test
    @DisplayName("Should return error message for empty data array")
    void emptyDataReturnsError() {
        String result = exportTools.createExcelFile("[]", "empty", toolContext);

        assertThat(result).contains("Error");
        assertThat(result).contains("empty");
    }

    @Test
    @DisplayName("Should sanitize filename with special characters")
    void sanitizeFilename() {
        String result = exportTools.createExcelFile(EXPORT_DATA, "my file/name:test", toolContext);

        assertThat(result).startsWith("File created successfully.");
        assertThat(result).contains("my_file_name_test");
        assertThat(result).doesNotContain("/name", ":test");
    }

    @Test
    @DisplayName("Should default to 'export' when filename is blank")
    void blankFilenameDefaults() {
        String result = exportTools.createExcelFile(EXPORT_DATA, "  ", toolContext);

        assertThat(result).startsWith("File created successfully.");
        assertThat(result).contains("export");
    }

    @Test
    @DisplayName("Should return error for invalid JSON input")
    void invalidJsonInput() {
        String result = exportTools.createExcelFile("not-json", "test", toolContext);

        assertThat(result).startsWith("Error creating Excel file:");
    }

    @Test
    @DisplayName("Should actually write the file to disk")
    void fileExistsOnDisk() {
        String result = exportTools.createExcelFile(EXPORT_DATA, "disk_test", toolContext);

        assertThat(result).contains("sandbox:/");
        // Extract filename from result
        String filename = result.substring(result.lastIndexOf("sandbox:/") + "sandbox:/".length());
        Path filePath = tempDir.resolve(TEST_USERNAME).resolve(filename);
        assertThat(filePath).exists();
        assertThat(filePath.toFile().length()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should work without SSE sink in context")
    void worksWithoutSseSink() {
        ToolContext ctx = new ToolContext(new HashMap<>());
        String result = exportTools.createExcelFile(EXPORT_DATA, "no_sink", ctx);

        assertThat(result).startsWith("File created successfully.");
    }
}
