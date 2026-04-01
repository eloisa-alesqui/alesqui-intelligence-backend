package es.alesqui.intelligence.service.chat.tools;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static es.alesqui.intelligence.service.chat.tools.support.SseSupport.emitStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Tools for lightweight data processing on JSON arrays.
 * 
 * Each public method is a separate Spring AI tool with only the parameters it
 * needs, making tool selection easier for the LLM. All tools share the same
 * filter syntax and are backed by common private helpers.
 */
public class DataTools {

    private static final String FILTER_DESCRIPTION =
        "Optional: JSONPath filter expression. Use `==` for equality, `&&` for AND. "
        + "For text matching, use `=~`. Examples: `@.price > 100`, "
        + "`@.address.city == 'Valencia'`, `@.name =~ '.*John.*'i` (case-insensitive).";

    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────────────────────────────────
    // Tool methods
    // ──────────────────────────────────────────────────────────────────

    @Tool(name = "count_data", description = "Counts elements in a JSON array, optionally after filtering.")
    public String countData(
        @ToolParam(description = "A JSON string of the data array.") String jsonData,
        @ToolParam(description = FILTER_DESCRIPTION) String filterExpression,
        ToolContext toolContext
    ) {
        return executeTool("count_data", filterExpression, toolContext, () -> {
            List<Map<String, Object>> data = parseAndFilter(jsonData, filterExpression);
            return objectMapper.writeValueAsString(Map.of("count", data.size()));
        });
    }

    @Tool(name = "filter_data", description = "Filters a JSON array and returns matching elements.")
    public String filterData(
        @ToolParam(description = "A JSON string of the data array.") String jsonData,
        @ToolParam(description = FILTER_DESCRIPTION) String filterExpression,
        ToolContext toolContext
    ) {
        return executeTool("filter_data", filterExpression, toolContext, () -> {
            List<Map<String, Object>> data = parseAndFilter(jsonData, filterExpression);
            return objectMapper.writeValueAsString(data);
        });
    }

    @Tool(name = "group_by_count", description = "Groups elements of a JSON array by a key and counts each group.")
    public String groupByCount(
        @ToolParam(description = "A JSON string of the data array.") String jsonData,
        @ToolParam(description = FILTER_DESCRIPTION) String filterExpression,
        @ToolParam(description = "Key to group by (supports nested keys like 'address.city').") String groupByKey,
        ToolContext toolContext
    ) {
        return executeTool("group_by_count", filterExpression, toolContext, () -> {
            List<Map<String, Object>> data = parseAndFilter(jsonData, filterExpression);
            Map<String, Long> result = data.stream()
                .collect(Collectors.groupingBy(
                    row -> String.valueOf(getNestedValue(row, groupByKey)),
                    Collectors.counting()));
            return objectMapper.writeValueAsString(result);
        });
    }

    @Tool(name = "group_by_date_count", description = "Groups elements by a date part (YEAR, MONTH, or QUARTER) extracted from a date field and counts each group.")
    public String groupByDateCount(
        @ToolParam(description = "A JSON string of the data array.") String jsonData,
        @ToolParam(description = FILTER_DESCRIPTION) String filterExpression,
        @ToolParam(description = "Key of the date field (ISO-8601 format).") String dateKey,
        @ToolParam(description = "Date part to group by: 'QUARTER', 'MONTH', or 'YEAR'.") String datePart,
        ToolContext toolContext
    ) {
        return executeTool("group_by_date_count", filterExpression, toolContext, () -> {
            List<Map<String, Object>> data = parseAndFilter(jsonData, filterExpression);
            Map<String, Long> result = data.stream()
                .collect(Collectors.groupingBy(
                    row -> extractDatePart(String.valueOf(getNestedValue(row, dateKey)), datePart),
                    Collectors.counting()));
            return objectMapper.writeValueAsString(result);
        });
    }

    @Tool(name = "sum_by_group", description = "Groups elements of a JSON array by a key and sums a numeric field for each group.")
    public String sumByGroup(
        @ToolParam(description = "A JSON string of the data array.") String jsonData,
        @ToolParam(description = FILTER_DESCRIPTION) String filterExpression,
        @ToolParam(description = "Key to group by.") String groupByKey,
        @ToolParam(description = "Key of the numeric field to sum.") String valueKey,
        ToolContext toolContext
    ) {
        return executeTool("sum_by_group", filterExpression, toolContext, () -> {
            List<Map<String, Object>> data = parseAndFilter(jsonData, filterExpression);
            ToDoubleFunction<Map<String, Object>> mapper = row -> Double.parseDouble(String.valueOf(getNestedValue(row, valueKey)));
            Map<String, Double> result = data.stream()
                .collect(Collectors.groupingBy(
                    row -> String.valueOf(getNestedValue(row, groupByKey)),
                    Collectors.summingDouble(mapper)));
            return objectMapper.writeValueAsString(result);
        });
    }

    @Tool(name = "average_by_group", description = "Groups elements of a JSON array by a key and averages a numeric field for each group.")
    public String averageByGroup(
        @ToolParam(description = "A JSON string of the data array.") String jsonData,
        @ToolParam(description = FILTER_DESCRIPTION) String filterExpression,
        @ToolParam(description = "Key to group by.") String groupByKey,
        @ToolParam(description = "Key of the numeric field to average.") String valueKey,
        ToolContext toolContext
    ) {
        return executeTool("average_by_group", filterExpression, toolContext, () -> {
            List<Map<String, Object>> data = parseAndFilter(jsonData, filterExpression);
            ToDoubleFunction<Map<String, Object>> mapper = row -> Double.parseDouble(String.valueOf(getNestedValue(row, valueKey)));
            Map<String, Double> result = data.stream()
                .collect(Collectors.groupingBy(
                    row -> String.valueOf(getNestedValue(row, groupByKey)),
                    Collectors.averagingDouble(mapper)));
            return objectMapper.writeValueAsString(result);
        });
    }

    // ──────────────────────────────────────────────────────────────────
    // Shared helpers
    // ──────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface ToolAction {
        String execute() throws Exception;
    }

    private String executeTool(String toolName, String filterExpression, ToolContext toolContext, ToolAction action) {
        emitStatus(toolContext, "Processing data with tool: '" + toolName + "'...");
        log.info("Executing tool: {} with filter '{}'", toolName, filterExpression);
        try {
            String result = action.execute();
            emitStatus(toolContext, "Data processing complete.");
            return result;
        } catch (Exception e) {
            log.error("Error executing {} tool: {}", toolName, e.getMessage(), e);
            emitStatus(toolContext, "Data processing failed.");
            return "{\"error\": \"Failed to process data. Reason: " + e.getMessage() + ". Please check your filter syntax or data structure.\"}";
        }
    }

    private List<Map<String, Object>> parseAndFilter(String jsonData, String filterExpression) throws Exception {
        List<Map<String, Object>> data = objectMapper.readValue(jsonData, new TypeReference<>() {});
        if (data.isEmpty()) return data;

        if (StringUtils.isNotBlank(filterExpression)) {
            String[] filters = filterExpression.split("&&");
            Predicate<Map<String, Object>> combinedPredicate = Stream.of(filters)
                .map(this::createFilterPredicate)
                .reduce(Predicate::and)
                .orElse(x -> true);
            data = data.stream().filter(combinedPredicate).collect(Collectors.toList());
        }
        return data;
    }

    private Predicate<Map<String, Object>> createFilterPredicate(String filterExpression) {
        String cleanExpression = filterExpression.replace("@.", "").trim();
        Pattern pattern = Pattern.compile("(.+?)(==|!=|>=|<=|>|<|=~)(.+)");
        Matcher matcher = pattern.matcher(cleanExpression);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid filter format. Expected: key[operator]value. Got: " + cleanExpression);
        }
        String key = matcher.group(1).trim();
        String operator = matcher.group(2).trim();
        String valueStr = matcher.group(3).trim();
        if ((valueStr.startsWith("'") && valueStr.endsWith("'")) || (valueStr.startsWith("\"") && valueStr.endsWith("\""))) {
            valueStr = valueStr.substring(1, valueStr.length() - 1);
        }
        final String finalValue = valueStr;

        if ("=~".equals(operator)) {
            Pattern regexPattern;
            if (finalValue.endsWith("i")) {
                String actualPattern = finalValue.substring(0, finalValue.length() - 1);
                regexPattern = Pattern.compile(actualPattern, Pattern.CASE_INSENSITIVE);
            } else {
                regexPattern = Pattern.compile(finalValue);
            }
            return map -> {
                String mapValueStr = String.valueOf(getNestedValue(map, key));
                return regexPattern.matcher(mapValueStr).find();
            };
        }

        return map -> {
            Object mapValue = getNestedValue(map, key);
            if (mapValue == null) return false;
            String mapValueStr = String.valueOf(mapValue);
            try {
                double mapValueNum = Double.parseDouble(mapValueStr);
                double filterValueNum = Double.parseDouble(finalValue);
                switch (operator) {
                    case "==": return mapValueNum == filterValueNum;
                    case "!=": return mapValueNum != filterValueNum;
                    case ">":  return mapValueNum > filterValueNum;
                    case "<":  return mapValueNum < filterValueNum;
                    case ">=": return mapValueNum >= filterValueNum;
                    case "<=": return mapValueNum <= filterValueNum;
                }
            } catch (NumberFormatException e) {
                int comparison = mapValueStr.compareToIgnoreCase(finalValue);
                switch (operator) {
                    case "==": return comparison == 0;
                    case "!=": return comparison != 0;
                    case ">":  return comparison > 0;
                    case "<":  return comparison < 0;
                    case ">=": return comparison >= 0;
                    case "<=": return comparison <= 0;
                }
            }
            return false;
        };
    }

    /**
     * Retrieves a value from a nested map using dot notation (e.g., "address.city").
     * Returns null when any part of the path does not exist.
     *
     * @param map the object represented as a map
     * @param nestedKey dot-separated key path
     * @return the value at the nested path or null
     */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> map, String nestedKey) {
        String[] parts = nestedKey.split("\\.");
        Object currentValue = map;
        for (String part : parts) {
            if (!(currentValue instanceof Map)) return null;
            currentValue = ((Map<String, Object>) currentValue).get(part);
            if (currentValue == null) return null;
        }
        return currentValue;
    }

    /**
     * Extracts a date part from an ISO-8601 date string. Supports OffsetDateTime
     * and LocalDateTime parsing.
     *
     * @param dateString the date text
     * @param part QUARTER, MONTH, or YEAR
     * @return the extracted part as a string, or an error label when parsing fails
     */
    private String extractDatePart(String dateString, String part) {
        if (dateString == null || "null".equals(dateString)) return "Invalid Date";
        try {
            TemporalAccessor parsedDate;
            try {
                parsedDate = OffsetDateTime.parse(dateString);
            } catch (DateTimeParseException e) {
                parsedDate = LocalDateTime.parse(dateString);
            }
            int monthValue = parsedDate.get(ChronoField.MONTH_OF_YEAR);
            int yearValue = parsedDate.get(ChronoField.YEAR);
            switch (part.toUpperCase()) {
                case "QUARTER":
                    return "Quarter " + ((monthValue - 1) / 3 + 1);
                case "MONTH":
                    return Month.of(monthValue).toString();
                case "YEAR":
                    return String.valueOf(yearValue);
                default:
                    return "Unknown Part";
            }
        } catch (DateTimeParseException e) {
            return "Invalid Date Format";
        }
    }
}
