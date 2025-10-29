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

import es.alesqui.intelligence.dto.chat.response.SseEvent;
import static es.alesqui.intelligence.service.chat.tools.support.SseSupport.getSinkFromContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Tools for lightweight data processing on JSON arrays.
 * 
 * This class performs in-memory operations such as filtering, counting,
 * grouping, and simple aggregations on a JSON array provided as a string. It
 * emits optional progress updates via an SSE sink when present in the
 * ToolContext. No external systems are contacted.
 */
public class DataTools {

    private final ObjectMapper objectMapper;

    /**
     * Analyzes a JSON array and applies a data operation.
     * 
     * Supported operations
     * - COUNT: counts elements after filtering.
     * - FILTER: returns the filtered array.
     * - GROUP_BY_COUNT: counts items by a key.
     * - GROUP_BY_DATE_PART_COUNT: counts items by YEAR, MONTH, or QUARTER extracted from a date field.
     * - SUM: sums a numeric value grouped by a key.
     * - AVERAGE: averages a numeric value grouped by a key.
     * 
     * Filtering syntax
     * - Provide a predicate using a simplified JSONPath-like syntax inside the description.
     * - Combine multiple predicates with &&.
     * - Text matches can use the regex operator =~ and an optional trailing i for case-insensitive.
     * 
     * @param jsonData JSON array of objects as string
     * @param operation one of COUNT, FILTER, GROUP_BY_COUNT, GROUP_BY_DATE_PART_COUNT, SUM, AVERAGE
     * @param filterExpression optional filter expression string
     * @param groupByKey optional key used for grouping
     * @param valueKey optional key whose values are aggregated
     * @param dateKey optional date key for date-based grouping
     * @param datePart optional date part: QUARTER, MONTH, or YEAR
     * @param toolContext optional context that may contain an SSE sink under key "sseSink"
     * @return a JSON string with the result or an error JSON when processing fails
     */
    @Tool(name = "process_data", description = "Analyzes a JSON array to perform operations like filtering, counting, grouping, or aggregating.")
    public String processData(
        @ToolParam(description = "A JSON string of the data array.") String jsonData,
        @ToolParam(description = "Operation to perform: 'COUNT', 'FILTER', 'GROUP_BY_COUNT', 'GROUP_BY_DATE_PART_COUNT', 'SUM', 'AVERAGE'.") String operation,
        @ToolParam(description = "Optional: JSONPath filter expression inside `[?()]`. Use `==` for equality, `&&` for AND. For text matching, use the regex operator `=~`. Examples: `@.price > 100`, `@.address.city == 'Valencia'`, `for 'starts with': @.address.zipCode =~ '^46.*'`, `for 'contains': @.name =~ '.*John.*'i` (case-insensitive)") String filterExpression,
        @ToolParam(description = "Optional: Key to group by.") String groupByKey,
        @ToolParam(description = "Optional: Key of values to aggregate (for SUM, AVERAGE).") String valueKey,
        @ToolParam(description = "Optional: Key of date values for date grouping.") String dateKey,
        @ToolParam(description = "Optional: Date part to group by: 'QUARTER', 'MONTH', 'YEAR'.") String datePart,
        ToolContext toolContext
    ) {
        Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) sink.tryEmitNext(SseEvent.status("Processing data with operation: '" + operation + "'..."));

        log.info("Executing tool: processData with operation '{}' and filter '{}'", operation, filterExpression);
        try {
            List<Map<String, Object>> data = objectMapper.readValue(jsonData, new TypeReference<>() {});
            if (data.isEmpty()) return "{\"result\": \"The provided JSON data is empty.\"}";

            Stream<Map<String, Object>> filteredStream = data.stream();
            if (StringUtils.isNotBlank(filterExpression)) {
                String[] filters = filterExpression.split("&&");
                Predicate<Map<String, Object>> combinedPredicate = Stream.of(filters)
                    .map(this::createFilterPredicate)
                    .reduce(Predicate::and)
                    .orElse(x -> true);
                filteredStream = filteredStream.filter(combinedPredicate);
            }
            List<Map<String, Object>> filteredData = filteredStream.collect(Collectors.toList());

            if (filteredData.isEmpty()) {
                 return "{\"result\": \"No data remains after applying the filter.\"}";
            }

            Object result;
            switch (operation.toUpperCase()) {
                case "COUNT":
                    result = Map.of("count", filteredData.size());
                    break;
                case "FILTER":
                    result = filteredData;
                    break;
                case "GROUP_BY_COUNT":
                    if (StringUtils.isBlank(groupByKey)) throw new IllegalArgumentException("'groupByKey' is required for GROUP_BY_COUNT.");
                    result = filteredData.stream().collect(Collectors.groupingBy(row -> String.valueOf(getNestedValue(row, groupByKey)), Collectors.counting()));
                    break;
                case "GROUP_BY_DATE_PART_COUNT":
                    if (StringUtils.isBlank(dateKey) || StringUtils.isBlank(datePart)) throw new IllegalArgumentException("'dateKey' and 'datePart' are required for GROUP_BY_DATE_PART_COUNT.");
                    result = filteredData.stream().collect(Collectors.groupingBy(row -> extractDatePart(String.valueOf(getNestedValue(row, dateKey)), datePart), Collectors.counting()));
                    break;
                case "SUM":
                case "AVERAGE":
                    if (StringUtils.isBlank(groupByKey) || StringUtils.isBlank(valueKey)) throw new IllegalArgumentException("'groupByKey' and 'valueKey' are required for aggregation.");
                    java.util.function.ToDoubleFunction<Map<String, Object>> mapper = row -> Double.parseDouble(String.valueOf(getNestedValue(row, valueKey)));
                    if (operation.equalsIgnoreCase("SUM")) {
                        result = filteredData.stream().collect(Collectors.groupingBy(row -> String.valueOf(getNestedValue(row, groupByKey)), Collectors.summingDouble(mapper)));
                    } else {
                        result = filteredData.stream().collect(Collectors.groupingBy(row -> String.valueOf(getNestedValue(row, groupByKey)), Collectors.averagingDouble(mapper)));
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation: " + operation);
            }

            if (sink != null) sink.tryEmitNext(SseEvent.status("Data processing complete."));
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error executing processData tool: {}", e.getMessage(), e);
            if (sink != null) sink.tryEmitNext(SseEvent.status("Data processing failed."));
            return "{\"error\": \"Failed to process data. Reason: " + e.getMessage() + ". Please check your filter syntax or data structure.\"}";
        }
    }

    /**
     * Parses a filter expression into a Java predicate. Supports numeric and
     * string comparisons, as well as regex matches via =~ with optional trailing i.
     *
     * @param filterExpression raw filter clause text
     * @return a predicate to apply to each JSON object (as a Map)
     */
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
