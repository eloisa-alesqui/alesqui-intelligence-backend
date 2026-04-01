package es.alesqui.intelligence.service.chat.tools;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.response.ChartData;
import static es.alesqui.intelligence.service.chat.tools.support.SseSupport.emitStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Tools that generate chart configurations from tabular JSON data.
 * 
 * This class converts arrays of JSON objects into chart configuration objects
 * that the frontend can render directly. It does not perform any remote calls
 * and only transforms provided data. Progress messages are optionally emitted
 * via an SSE sink when present in the ToolContext.
 */
public class ChartTools {

    private final ObjectMapper objectMapper;

    /**
     * Builds a chart configuration from a JSON array of records.
     * 
     * The method extracts labels and values from the provided keys, assigns a
     * color palette, and returns a chart configuration usable by the UI. It
     * supports common types like bar, pie, and line.
     * 
     * @param chartType chart type, e.g. "bar", "pie", or "line"
     * @param jsonData JSON array of objects as string
     * @param labelKey field name used for labels
     * @param dataKey field name used for numeric or categorical values
     * @param datasetLabel label to display in the legend
     * @param toolContext optional context that may contain an SSE sink under key "sseSink"
     * @return a ChartData structure ready for rendering
     * @throws RuntimeException if input cannot be parsed or transformed
     */
    @Tool(name = "create_chart", description = "Creates a chart configuration object from JSON data.")
    public ChartData createChart(
        @ToolParam(description = "The type of chart. Supported: 'bar', 'pie', 'line'.") String chartType,
        @ToolParam(description = "A JSON string of the data array.") String jsonData,
        @ToolParam(description = "The key for the chart's labels.") String labelKey,
        @ToolParam(description = "The key for the chart's data values.") String dataKey,
        @ToolParam(description = "A descriptive title for the dataset.") String datasetLabel,
        ToolContext toolContext
    ) {
        emitStatus(toolContext, "Generating '" + chartType + "' chart configuration...");

        try {
            List<Map<String, Object>> data = objectMapper.readValue(jsonData, new TypeReference<>() {});
            List<String> labels = data.stream().map(row -> String.valueOf(row.get(labelKey))).collect(Collectors.toList());
            List<Object> values = data.stream().map(row -> row.get(dataKey)).collect(Collectors.toList());

            ChartData.ChartDataset dataset = ChartData.ChartDataset.builder()
                .label(datasetLabel).data(values).backgroundColor(generateColors(values.size())).borderWidth(1).build();
            ChartData.ChartConfigData configData = ChartData.ChartConfigData.builder()
                .labels(labels).datasets(List.of(dataset)).build();

            emitStatus(toolContext, "Chart configuration created.");
            return ChartData.builder().type(chartType).data(configData).options(Map.of("responsive", true)).build();
        } catch (Exception e) {
            log.error("Error creating chart data", e);
            emitStatus(toolContext, "Failed to generate chart configuration.");
            throw new RuntimeException("Error creating chart data: " + e.getMessage());
        }
    }

    /**
     * Generates a list of visually distinct RGBA colors using the golden ratio
     * conjugate to space hues evenly.
     * 
     * @param count number of colors requested
     * @return a list of colors in "rgba(r, g, b, a)" format; an empty list when count is non-positive
     */
    private List<String> generateColors(int count) {
        if (count <= 0) return List.of();
        final float GOLDEN_RATIO_CONJUGATE = 0.61803398875f;
        float initialHue = new Random().nextFloat();
        return Stream.iterate(initialHue, hue -> (hue + GOLDEN_RATIO_CONJUGATE) % 1.0f)
            .limit(count)
            .map(hue -> {
                Color color = Color.getHSBColor(hue, 0.8f, 0.9f);
                return String.format("rgba(%d, %d, %d, 0.7)", color.getRed(), color.getGreen(), color.getBlue());
            })
            .collect(Collectors.toList());
    }
}
