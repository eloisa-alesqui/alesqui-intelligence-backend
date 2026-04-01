package es.alesqui.intelligence.service.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.response.ChartData;

@DisplayName("ChartTools")
class ChartToolsTest {

    private ChartTools chartTools;
    private ToolContext toolContext;

    private static final String CHART_DATA = """
            [
                {"month": "January", "sales": 120},
                {"month": "February", "sales": 200},
                {"month": "March", "sales": 150}
            ]
            """;

    @BeforeEach
    void setUp() {
        chartTools = new ChartTools(new ObjectMapper());
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(ToolContextKeys.USER_ID, "test-user-id");
        toolContext = new ToolContext(contextMap);
    }

    @Test
    @DisplayName("Should create a bar chart with correct structure")
    void createBarChart() {
        ChartData result = chartTools.createChart("bar", CHART_DATA, "month", "sales", "Monthly Sales", toolContext);

        assertThat(result.getType()).isEqualTo("bar");
        assertThat(result.getData().getLabels()).containsExactly("January", "February", "March");
        assertThat(result.getData().getDatasets()).hasSize(1);
        assertThat(result.getData().getDatasets().get(0).getLabel()).isEqualTo("Monthly Sales");
        assertThat(result.getData().getDatasets().get(0).getData()).containsExactly(120, 200, 150);
        assertThat(result.getOptions()).containsEntry("responsive", true);
    }

    @Test
    @DisplayName("Should create a pie chart")
    void createPieChart() {
        ChartData result = chartTools.createChart("pie", CHART_DATA, "month", "sales", "Sales Distribution", toolContext);

        assertThat(result.getType()).isEqualTo("pie");
        assertThat(result.getData().getLabels()).hasSize(3);
    }

    @Test
    @DisplayName("Should generate correct number of colors matching data size")
    void colorCountMatchesDataSize() {
        ChartData result = chartTools.createChart("bar", CHART_DATA, "month", "sales", "Sales", toolContext);

        assertThat(result.getData().getDatasets().get(0).getBackgroundColor()).hasSize(3);
        assertThat(result.getData().getDatasets().get(0).getBackgroundColor())
                .allMatch(color -> color.startsWith("rgba("));
    }

    @Test
    @DisplayName("Should throw RuntimeException for invalid JSON input")
    void invalidJsonInput() {
        assertThatThrownBy(() ->
                chartTools.createChart("bar", "not-json", "x", "y", "Label", toolContext))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error creating chart data");
    }

    @Test
    @DisplayName("Should handle single-element data")
    void singleElementData() {
        String singleItem = """
                [{"category": "A", "value": 42}]
                """;
        ChartData result = chartTools.createChart("bar", singleItem, "category", "value", "Test", toolContext);

        assertThat(result.getData().getLabels()).containsExactly("A");
        assertThat(result.getData().getDatasets().get(0).getData()).containsExactly(42);
        assertThat(result.getData().getDatasets().get(0).getBackgroundColor()).hasSize(1);
    }

    @Test
    @DisplayName("Should work without SSE sink in context")
    void worksWithoutSseSink() {
        ToolContext ctx = new ToolContext(new HashMap<>());
        ChartData result = chartTools.createChart("line", CHART_DATA, "month", "sales", "Sales", ctx);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("line");
    }
}
