package es.alesqui.intelligence.dto.chat.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * Represents the complete configuration for a chart to be rendered in the frontend.
 * This object is compatible with libraries like Chart.js.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartData {

    /**
     * The type of chart to be rendered.
     * Example values: "bar", "pie", "line".
     */
    private String type;

    /**
     * The main data object for the chart, containing labels and datasets.
     */
    private ChartConfigData data;

    /**
     * A map of options to configure the chart's behavior and appearance.
     * Example: {"responsive": true}
     */
    private Map<String, Object> options;

    /**
     * Contains the core data configuration for the chart, including labels
     * that appear on the axes and the datasets to be plotted.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartConfigData {

        /**
         * A list of strings representing the labels for the x-axis (for bar/line charts)
         * or the segments (for pie charts).
         */
        private List<String> labels;

        /**
         * A list of datasets to be displayed on the chart. Each dataset
         * corresponds to a distinct set of data, e.g., a single line in a line chart.
         */
        private List<ChartDataset> datasets;
    }

    /**
     * Represents a single set of data to be plotted on the chart.
     * For example, in a bar chart comparing sales across years, each year
     * would be a separate dataset.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartDataset {

        /**
         * The descriptive name for this dataset, which appears in the legend and tooltips.
         * Example: "Sales 2024".
         */
        private String label;

        /**
         * The actual data points to be plotted. The list can contain numbers
         * or other objects depending on the chart type.
         */
        private List<Object> data;

        /**
         * A list of colors for the data points. For a bar chart, this would be the
         * fill color of each bar. The size of this list should typically match the
         * size of the 'data' list.
         * The format should be a string like "rgba(255, 99, 132, 0.6)".
         */
        private List<String> backgroundColor;

        /**
         * A list of border colors for the data points. For a bar chart, this would
         * be the color of the line around each bar.
         */
        private List<String> borderColor;

        /**
         * The width of the border around each data point, in pixels.
         */
        private int borderWidth;
    }
}