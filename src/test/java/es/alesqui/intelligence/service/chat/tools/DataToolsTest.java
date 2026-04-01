package es.alesqui.intelligence.service.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("DataTools")
class DataToolsTest {

    private DataTools dataTools;
    private ToolContext toolContext;

    private static final String SAMPLE_DATA = """
            [
                {"name": "Alice", "age": 30, "city": "Valencia", "salary": 50000, "date": "2024-01-15T10:00:00"},
                {"name": "Bob", "age": 25, "city": "Madrid", "salary": 40000, "date": "2024-04-20T10:00:00"},
                {"name": "Charlie", "age": 35, "city": "Valencia", "salary": 60000, "date": "2024-07-10T10:00:00"},
                {"name": "Diana", "age": 28, "city": "Barcelona", "salary": 45000, "date": "2023-11-05T10:00:00"},
                {"name": "Eve", "age": 32, "city": "Valencia", "salary": 55000, "date": "2023-03-22T10:00:00"}
            ]
            """;

    private static final String NESTED_DATA = """
            [
                {"name": "Alice", "address": {"city": "Valencia", "zip": "46001"}},
                {"name": "Bob", "address": {"city": "Madrid", "zip": "28001"}},
                {"name": "Charlie", "address": {"city": "Valencia", "zip": "46002"}}
            ]
            """;

    @BeforeEach
    void setUp() {
        dataTools = new DataTools(new ObjectMapper());
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(ToolContextKeys.USER_ID, "test-user-id");
        toolContext = new ToolContext(contextMap);
    }

    @Nested
    @DisplayName("count_data")
    class CountData {

        @Test
        @DisplayName("Should count all elements without filter")
        void countAll() {
            String result = dataTools.countData(SAMPLE_DATA, null, toolContext);
            assertThat(result).isEqualTo("{\"count\":5}");
        }

        @Test
        @DisplayName("Should count elements matching a filter")
        void countWithFilter() {
            String result = dataTools.countData(SAMPLE_DATA, "@.city == 'Valencia'", toolContext);
            assertThat(result).isEqualTo("{\"count\":3}");
        }

        @Test
        @DisplayName("Should return zero for empty array")
        void countEmptyArray() {
            String result = dataTools.countData("[]", null, toolContext);
            assertThat(result).isEqualTo("{\"count\":0}");
        }

        @Test
        @DisplayName("Should return error for invalid JSON")
        void countInvalidJson() {
            String result = dataTools.countData("not-json", null, toolContext);
            assertThat(result).contains("\"error\"");
        }

        @Test
        @DisplayName("Should return error for invalid filter syntax")
        void countInvalidFilter() {
            String result = dataTools.countData(SAMPLE_DATA, "invalid filter !!", toolContext);
            assertThat(result).contains("\"error\"");
        }

        @Test
        @DisplayName("Should count with blank filter as no filter")
        void countWithBlankFilter() {
            String result = dataTools.countData(SAMPLE_DATA, "   ", toolContext);
            assertThat(result).isEqualTo("{\"count\":5}");
        }
    }

    @Nested
    @DisplayName("filter_data")
    class FilterData {

        @Test
        @DisplayName("Should filter by equality")
        void filterByEquality() {
            String result = dataTools.filterData(SAMPLE_DATA, "@.city == 'Madrid'", toolContext);
            assertThat(result).contains("Bob");
            assertThat(result).doesNotContain("Alice", "Charlie");
        }

        @Test
        @DisplayName("Should filter by numeric comparison")
        void filterByNumericComparison() {
            String result = dataTools.filterData(SAMPLE_DATA, "@.age > 30", toolContext);
            assertThat(result).contains("Charlie", "Eve");
            assertThat(result).doesNotContain("Bob", "Diana");
        }

        @Test
        @DisplayName("Should filter by regex")
        void filterByRegex() {
            String result = dataTools.filterData(SAMPLE_DATA, "@.name =~ .*li.*i", toolContext);
            assertThat(result).contains("Alice", "Charlie");
            assertThat(result).doesNotContain("Bob");
        }

        @Test
        @DisplayName("Should filter with AND conditions")
        void filterWithAnd() {
            String result = dataTools.filterData(SAMPLE_DATA, "@.city == 'Valencia' && @.age > 31", toolContext);
            assertThat(result).contains("Charlie", "Eve");
            assertThat(result).doesNotContain("Alice");
        }

        @Test
        @DisplayName("Should return empty array when no matches")
        void filterNoMatches() {
            String result = dataTools.filterData(SAMPLE_DATA, "@.city == 'Sevilla'", toolContext);
            assertThat(result).isEqualTo("[]");
        }

        @Test
        @DisplayName("Should filter by inequality")
        void filterByInequality() {
            String result = dataTools.filterData(SAMPLE_DATA, "@.city != 'Valencia'", toolContext);
            assertThat(result).contains("Bob", "Diana");
            assertThat(result).doesNotContain("Alice", "Charlie", "Eve");
        }
    }

    @Nested
    @DisplayName("group_by_count")
    class GroupByCount {

        @Test
        @DisplayName("Should group by flat key")
        void groupByFlatKey() {
            String result = dataTools.groupByCount(SAMPLE_DATA, null, "city", toolContext);
            assertThat(result).contains("\"Valencia\":3");
            assertThat(result).contains("\"Madrid\":1");
            assertThat(result).contains("\"Barcelona\":1");
        }

        @Test
        @DisplayName("Should group by nested key")
        void groupByNestedKey() {
            String result = dataTools.groupByCount(NESTED_DATA, null, "address.city", toolContext);
            assertThat(result).contains("\"Valencia\":2");
            assertThat(result).contains("\"Madrid\":1");
        }

        @Test
        @DisplayName("Should group with filter applied first")
        void groupWithFilter() {
            String result = dataTools.groupByCount(SAMPLE_DATA, "@.age >= 30", "city", toolContext);
            assertThat(result).contains("\"Valencia\":3");
            assertThat(result).doesNotContain("Madrid");
            assertThat(result).doesNotContain("Barcelona");
        }
    }

    @Nested
    @DisplayName("group_by_date_count")
    class GroupByDateCount {

        @Test
        @DisplayName("Should group by YEAR")
        void groupByYear() {
            String result = dataTools.groupByDateCount(SAMPLE_DATA, null, "date", "YEAR", toolContext);
            assertThat(result).contains("\"2024\":3");
            assertThat(result).contains("\"2023\":2");
        }

        @Test
        @DisplayName("Should group by MONTH")
        void groupByMonth() {
            String result = dataTools.groupByDateCount(SAMPLE_DATA, null, "date", "MONTH", toolContext);
            assertThat(result).contains("JANUARY");
            assertThat(result).contains("APRIL");
            assertThat(result).contains("JULY");
            assertThat(result).contains("NOVEMBER");
            assertThat(result).contains("MARCH");
        }

        @Test
        @DisplayName("Should group by QUARTER")
        void groupByQuarter() {
            String result = dataTools.groupByDateCount(SAMPLE_DATA, null, "date", "QUARTER", toolContext);
            assertThat(result).contains("Quarter 1");
            assertThat(result).contains("Quarter 2");
            assertThat(result).contains("Quarter 3");
            assertThat(result).contains("Quarter 4");
        }

        @Test
        @DisplayName("Should handle null date field gracefully")
        void groupByDateWithNullField() {
            String data = """
                    [{"name": "Alice", "date": null}, {"name": "Bob", "date": "2024-01-15T10:00:00"}]
                    """;
            String result = dataTools.groupByDateCount(data, null, "date", "YEAR", toolContext);
            assertThat(result).contains("Invalid Date");
            assertThat(result).contains("2024");
        }
    }

    @Nested
    @DisplayName("sum_by_group")
    class SumByGroup {

        @Test
        @DisplayName("Should sum numeric field grouped by key")
        void sumByCity() {
            String result = dataTools.sumByGroup(SAMPLE_DATA, null, "city", "salary", toolContext);
            assertThat(result).contains("\"Valencia\"");
            assertThat(result).contains("165000");
        }

        @Test
        @DisplayName("Should sum with filter")
        void sumWithFilter() {
            String result = dataTools.sumByGroup(SAMPLE_DATA, "@.age > 30", "city", "salary", toolContext);
            assertThat(result).contains("\"Valencia\"");
            assertThat(result).doesNotContain("Madrid");
        }

        @Test
        @DisplayName("Should return error for non-numeric value key")
        void sumNonNumericField() {
            String result = dataTools.sumByGroup(SAMPLE_DATA, null, "city", "name", toolContext);
            assertThat(result).contains("\"error\"");
        }
    }

    @Nested
    @DisplayName("average_by_group")
    class AverageByGroup {

        @Test
        @DisplayName("Should average numeric field grouped by key")
        void averageByCity() {
            String result = dataTools.averageByGroup(SAMPLE_DATA, null, "city", "age", toolContext);
            assertThat(result).contains("\"Valencia\"");
            assertThat(result).contains("\"Madrid\"");
            assertThat(result).contains("\"Barcelona\"");
        }

        @Test
        @DisplayName("Should compute correct average for single-element group")
        void averageSingleElementGroup() {
            String result = dataTools.averageByGroup(SAMPLE_DATA, null, "city", "age", toolContext);
            // Madrid has only Bob (age 25)
            assertThat(result).contains("\"Madrid\":25.0");
        }
    }

    @Nested
    @DisplayName("SSE events")
    class SseEvents {

        @Test
        @DisplayName("Should work with null SSE sink in context")
        void worksWithoutSseSink() {
            ToolContext ctx = new ToolContext(Map.of(ToolContextKeys.USER_ID, "u1"));
            String result = dataTools.countData(SAMPLE_DATA, null, ctx);
            assertThat(result).isEqualTo("{\"count\":5}");
        }

        @Test
        @DisplayName("Should work with empty context map")
        void worksWithEmptyContext() {
            ToolContext ctx = new ToolContext(new HashMap<>());
            String result = dataTools.countData(SAMPLE_DATA, null, ctx);
            assertThat(result).isEqualTo("{\"count\":5}");
        }
    }
}
