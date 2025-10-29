package es.alesqui.intelligence.service.chat.tools;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.response.SseEvent;
import static es.alesqui.intelligence.service.chat.tools.support.SseSupport.getSinkFromContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Tools for exporting data into downloadable artifacts.
 * 
 * Currently supports generating Excel (.xlsx) files from JSON arrays. Files are
 * written to a temporary directory and exposed via a sandbox link for download.
 * Progress updates are optionally emitted via an SSE sink present in the ToolContext.
 */
public class ExportTools {

    private final ObjectMapper objectMapper;
    private final Path tempFileDir = Paths.get(System.getProperty("java.io.tmpdir"), "alesqui-intelligence-files");

    /**
     * Creates an Excel file from a JSON array of objects and returns a sandbox
     * download link. The first object's keys determine the header order. Values
     * are stringified for insertion into the sheet.
     * 
     * @param jsonData JSON array of objects as string
     * @param filename desired base filename without extension; sanitized for safety
     * @param toolContext optional context that may contain an SSE sink under key "sseSink"
     * @return a message containing a sandbox link to the generated file, or an error message
     */
    @Tool(name = "create_excel_file", description = "Creates an Excel file from JSON data and returns a download link.")
    public String createExcelFile(
        @ToolParam(description = "A JSON string representing an array of objects.") String jsonData,
        @ToolParam(description = "A descriptive name for the file, without the extension.") String filename,
        ToolContext toolContext
    ) {
        Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) sink.tryEmitNext(SseEvent.status("Creating Excel file: " + filename + ".xlsx..."));

        try {
            if (!Files.exists(tempFileDir)) Files.createDirectories(tempFileDir);

            if (sink != null) sink.tryEmitNext(SseEvent.status("Parsing JSON data..."));
            List<Map<String, Object>> data = objectMapper.readValue(jsonData, new TypeReference<>() {});
            if (data.isEmpty()) return "Error: Cannot create an empty file. The JSON data was empty.";

            if (sink != null) sink.tryEmitNext(SseEvent.status("Building Excel structure from data..."));
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Data");

            Row headerRow = sheet.createRow(0);
            List<String> headers = new ArrayList<>(data.get(0).keySet());
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }

            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                Map<String, Object> rowData = data.get(i);
                for (int j = 0; j < headers.size(); j++) {
                    Object value = rowData.get(headers.get(j));
                    row.createCell(j).setCellValue(value != null ? value.toString() : "");
                }
            }

            if (sink != null) sink.tryEmitNext(SseEvent.status("Writing file to temporary storage..."));
            String safeBase = StringUtils.defaultIfBlank(filename, "export").replaceAll("[^a-zA-Z0-9.-]", "_");
            String uniqueFilename = safeBase + "_" + UUID.randomUUID().toString().substring(0, 8) + ".xlsx";
            Path filePath = tempFileDir.resolve(uniqueFilename);

            try (FileOutputStream fileOut = new FileOutputStream(filePath.toFile())) {
                workbook.write(fileOut);
            }
            workbook.close();

            if (sink != null) sink.tryEmitNext(SseEvent.status("Excel file created successfully."));
            return "File created successfully. Download link: sandbox:/" + uniqueFilename;

        } catch (Exception e) {
            log.error("Error creating Excel file", e);
            if (sink != null) sink.tryEmitNext(SseEvent.status("Failed to create Excel file."));
            return "Error creating Excel file: " + e.getMessage();
        }
    }
}
