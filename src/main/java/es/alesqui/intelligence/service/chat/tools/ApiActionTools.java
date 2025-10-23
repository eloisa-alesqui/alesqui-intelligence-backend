package es.alesqui.intelligence.service.chat.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.alesqui.intelligence.dto.chat.request.ApiCallRequest;
import es.alesqui.intelligence.dto.chat.response.ApiCallResponse;
import es.alesqui.intelligence.dto.chat.response.ChartData;
import es.alesqui.intelligence.dto.chat.response.SseEvent;
import es.alesqui.intelligence.dto.chat.response.StructuredApiError;
import es.alesqui.intelligence.exception.ApiExecutionException;
import es.alesqui.intelligence.exception.ParameterValidationException;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedEndpoint;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedMediaType;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedParameter;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedRequestBody;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedSchema;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedTag;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.api.ApiExecutionService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A service that provides a collection of tools for the AI model.
 * These tools allow the AI to interact with external systems, such as listing APIs,
 * calling them, processing data, and creating files or charts. Each tool is
 * responsible for emitting its own status updates via the SseEventSinkHolder.
 */
@Slf4j
@Service
public class ApiActionTools {

    private final UnifiedApiService unifiedApiService;
    private final ApiExecutionService apiExecutionService;
    private final ObjectMapper objectMapper;
    
    private final Path tempFileDir = Paths.get(System.getProperty("java.io.tmpdir"), "alesqui-intelligence-files");
    
    public ApiActionTools(
            UnifiedApiService unifiedApiService,
            ApiExecutionService apiExecutionService,
            ObjectMapper objectMapper
    ) {
        this.unifiedApiService = unifiedApiService;
        this.apiExecutionService = apiExecutionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all available APIs. This is the first step for the AI to understand
     * what systems it can interact with.
     * @return A formatted string listing the available APIs.
     */
    @Tool(name = "list_apis", description = "Lists all available APIs with their descriptions, tags, and a summary of their capabilities.")
    public String listApis(ToolContext toolContext) {
    	Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) sink.tryEmitNext(SseEvent.status("Listing available APIs..."));

        log.info("Executing tool: listApis");
        try {
            var apis = unifiedApiService.findActiveApis().collectList().block(Duration.ofSeconds(10));
            if (apis == null || apis.isEmpty()) {
            	if (sink != null) sink.tryEmitNext(SseEvent.status("No APIs found."));
                return "No available APIs were found.";
            }

            if (sink != null) sink.tryEmitNext(SseEvent.status("Found " + apis.size() + " APIs."));
            
            return "Available APIs:\n" + apis.stream()
                .map(api -> {
                    String tagsString = api.getTags() != null ? api.getTags().stream().map(UnifiedTag::getName).collect(Collectors.joining(", ")) : "none";
                    String capabilities = StringUtils.isNotBlank(api.getCapabilitiesSummary()) ? api.getCapabilitiesSummary() : "Not available";
                    return String.format("• Name: %s\n  - Description: %s\n  - Tags: [%s]\n  - Capabilities: %s", api.getName(), api.getDescription(), tagsString, capabilities);
                })
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Error in listApis tool", e);
            if (sink != null) sink.tryEmitNext(SseEvent.status("Failed to list APIs."));
            return "Error while trying to list APIs: " + e.getMessage();
        }
    }
    
    /**
     * Inspects the detailed schema of a requestBody for a given API endpoint.
     * This tool now correctly navigates the content map to find the schema.
     *
     * @param apiName The name of the API (e.g., "ecommerce").
     * @param operationId The operation ID of the endpoint (e.g., "createUser").
     * @return A Markdown string detailing the requestBody's schema, or an error message.
     */
    @Tool(name = "inspect_request_body_schema", description = "Inspects the detailed schema (including required fields and types) for an API endpoint's requestBody. Use this when you need to know what JSON data to send for a POST/PUT operation.")
    public String inspectRequestBodySchema(
            @ToolParam(description = "The exact name of the API.") String apiName,
            @ToolParam(description = "The operation ID of the endpoint.") String operationId,
            ToolContext toolContext) {
        
        Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) sink.tryEmitNext(SseEvent.status("Inspecting request body schema for API: " + apiName + ", Operation: " + operationId + "..."));
        
        log.info("Executing tool: inspect_request_body_schema - API: '{}', Operation: '{}'", apiName, operationId);

        try {
            UnifiedApiDocument api = unifiedApiService.findByName(apiName.trim()).block(Duration.ofSeconds(10));
            if (api == null) throw new IllegalArgumentException("No API found with name: " + apiName);

            UnifiedEndpoint endpoint = api.getEndpoints().stream()
                    .filter(e -> operationId.trim().equals(e.getOperationId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No endpoint found with operation ID '" + operationId + "' in API '" + apiName + "'"));

            UnifiedRequestBody requestBody = endpoint.getRequestBody();
            if (requestBody == null || requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
                return "The endpoint '" + operationId + "' in API '" + apiName + "' does not define a request body.";
            }

            UnifiedMediaType mediaType = requestBody.getContent().get("application/json");

            if (mediaType == null) {
                mediaType = requestBody.getContent().values().stream().findFirst().orElse(null);
            }

            if (mediaType == null || mediaType.getSchema() == null) {
                return "The endpoint '" + operationId + "' does not define a usable schema for its request body.";
            }

            UnifiedSchema schemaToRender = mediaType.getSchema();

            if (schemaToRender.getRef() != null && !schemaToRender.getRef().isBlank()) {
                String schemaName = schemaToRender.getRef().substring(schemaToRender.getRef().lastIndexOf('/') + 1);
                
                Map<String, UnifiedSchema> allApiSchemas = api.getSchemas();
                if (allApiSchemas != null && allApiSchemas.containsKey(schemaName)) {
                    log.debug("Resolving schema reference for '{}'", schemaName);
                    schemaToRender = allApiSchemas.get(schemaName);
                    
                    if (schemaToRender.getTitle() == null) {
                        schemaToRender.setTitle(schemaName);
                    }
                } else {
                    throw new IllegalStateException("Schema reference '" + schemaName + "' could not be found in the API definition.");
                }
            }

            return "```markdown\n" + schemaToRender.toMarkdown() + "\n```";

        } catch (Exception e) {
            log.error("Error inspecting request body schema for API: '{}', Operation: '{}'", apiName, operationId, e);
            return "Error inspecting schema: " + e.getMessage();
        }
    }

    /**
     * Gets the endpoints (operations) for a specific API.
     * @param apiName The exact name of the API to inspect.
     * @return A formatted string listing the endpoints for the specified API.
     */
    @Tool(name = "list_endpoints", description = "Gets the endpoints (operations) for a specific API, including their required parameters.")
    public String listEndpoints(@ToolParam(description = "The exact name of the API to inspect.") String apiName, ToolContext toolContext) {
    	Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) sink.tryEmitNext(SseEvent.status("Listing endpoints for API: " + apiName + "..."));

        log.info("Executing tool: listEndpoints for API '{}'", apiName);
        try {
            UnifiedApiDocument api = unifiedApiService.findByName(apiName.trim()).block(Duration.ofSeconds(10));
            if (api == null) {
                return "Error: No API was found with the name: " + apiName;
            }
            if (api.getEndpoints() == null || api.getEndpoints().isEmpty()) {
                return "The API '" + apiName + "' has no available endpoints.";
            }

            if (sink != null) sink.tryEmitNext(SseEvent.status("Found " + api.getEndpoints().size() + " endpoints for " + apiName + "."));
            return "Available endpoints for API '" + apiName + "':\n" + api.getEndpoints().stream()
                .map(e -> String.format("• OperationId: %s, Method: %s, Description: %s\n%s", e.getOperationId(), e.getMethod(), e.getSummary(), formatParametersForLLM(e.getParameters())))
                .collect(Collectors.joining("\n\n")); 
        } catch (Exception e) {
            log.error("Error in listEndpoints tool for API '{}'", apiName, e);
            if (sink != null) sink.tryEmitNext(SseEvent.status("Failed to list endpoints."));
            return "Error listing endpoints for '" + apiName + "': " + e.getMessage();
        }
    }
    
    /**
     * Calls a specific API endpoint with the provided parameters.
     * @param apiName The name of the API to call.
     * @param operationId The operation ID of the endpoint to call.
     * @param parameters JSON string with the parameters for the API call.
     * @param toolContext Context provided by the AI model, used here to get the conversationId.
     * @return An ApiCallResponse object containing the result of the API call.
     */
    @Tool(name = "call_api", description = "Calls a specific API endpoint with the provided parameters.")
    public ApiCallResponse callApi(
        @ToolParam(description = "The exact name of the API to call.") String apiName,
        @ToolParam(description = "The operation ID of the endpoint to call.") String operationId,
        @ToolParam(description = "JSON string with the parameters for the API call.") String parameters,
        ToolContext toolContext) {
        
    	Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) sink.tryEmitNext(SseEvent.status("Preparing API call to operation: " + operationId + "..."));

        log.info("Executing tool: callApi - API: '{}', Operation: '{}'", apiName, operationId);
        
        try {
            validateApiCallInputs(apiName, operationId);
            
            UnifiedApiDocument api = unifiedApiService.findByName(apiName.trim()).block(Duration.ofSeconds(10));
            if (api == null) throw new ApiExecutionException("No API found with name: " + apiName);
            
            UnifiedEndpoint endpoint = api.getEndpoints().stream()
                .filter(e -> operationId.trim().equals(e.getOperationId())).findFirst()
                .orElseThrow(() -> new ApiExecutionException("No endpoint found with operation ID '" + operationId + "' in API '" + apiName + "'"));
            
            Map<String, Object> paramMap = parseParameters(parameters);
            validateParametersAgainstSpec(endpoint, paramMap);
            
            String conversationId = (String) toolContext.getContext().get("conversationId");
            ApiCallRequest apiCallRequest = buildApiCallRequest(api, endpoint, paramMap, conversationId);
            
            if (sink != null) sink.tryEmitNext(SseEvent.status("Executing API call..."));
            ApiCallResponse response = apiExecutionService.executeApiCall(apiCallRequest).block(Duration.ofSeconds(30));
            if (response == null) throw new ApiExecutionException("No response received from API call");

            if (sink != null) sink.tryEmitNext(SseEvent.status("API call to '" + operationId + "' was successful."));
            return response;
            
        } catch (Exception e) {
            log.error("Error in callApi tool - API: '{}', Operation: '{}'", apiName, operationId, e);
            if (sink != null) sink.tryEmitNext(SseEvent.status("API call to '" + operationId + "' failed."));
            return createStructuredErrorResponse(apiName, operationId, e);
        }
    }
    
    /**
     * Creates an Excel file from JSON data and returns a download link.
     * @param jsonData A JSON string representing an array of objects.
     * @param filename A descriptive name for the file.
     * @return A string containing a download link or an error message.
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
            String uniqueFilename = filename.replaceAll("[^a-zA-Z0-9.-]", "_") + "_" + UUID.randomUUID().toString().substring(0, 8) + ".xlsx";
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

    /**
     * Creates a chart configuration object from JSON data.
     * @param chartType The type of chart to create (e.g., 'bar', 'pie', 'line').
     * @param jsonData A JSON string of the data to plot.
     * @param labelKey The key in the JSON objects for chart labels.
     * @param dataKey The key in the JSON objects for data values.
     * @param datasetLabel A descriptive title for the dataset.
     * @return A ChartData object for rendering on the frontend.
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
    	Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
    	if (sink != null) sink.tryEmitNext(SseEvent.status("Generating '" + chartType + "' chart configuration..."));
    	
        try {
            List<Map<String, Object>> data = objectMapper.readValue(jsonData, new TypeReference<>() {});
            List<String> labels = data.stream().map(row -> String.valueOf(row.get(labelKey))).collect(Collectors.toList());
            List<Object> values = data.stream().map(row -> row.get(dataKey)).collect(Collectors.toList());

            ChartData.ChartDataset dataset = ChartData.ChartDataset.builder()
                .label(datasetLabel).data(values).backgroundColor(generateColors(values.size())).borderWidth(1).build();
            ChartData.ChartConfigData configData = ChartData.ChartConfigData.builder()
                .labels(labels).datasets(List.of(dataset)).build();
            
            if (sink != null) sink.tryEmitNext(SseEvent.status("Chart configuration created."));
            return ChartData.builder().type(chartType).data(configData).options(Map.of("responsive", true)).build();
        } catch (Exception e) {
            log.error("Error creating chart data", e);
            if (sink != null) sink.tryEmitNext(SseEvent.status("Failed to generate chart configuration."));
            throw new RuntimeException("Error creating chart data: " + e.getMessage());
        }
    }
    
    /**
     * Analyzes a JSON array to perform filtering, counting, grouping, or aggregation.
     * @param jsonData A JSON string representing an array of objects.
     * @param operation The operation to perform.
     * @param filterExpression An optional filter expression.
     * @param groupByKey The key to group by for grouping or aggregation.
     * @param valueKey The key whose values to sum or average.
     * @param dateKey The key containing a date string for date-based grouping.
     * @param datePart The part of the date to group by.
     * @return A JSON string representing the result of the operation.
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
                    .orElse(x -> true); // Si no hay filtros, no se filtra nada
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
                    } else { // AVERAGE
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
    
    // --- PRIVATE HELPER METHODS ---
    
    /**
     * Creates a Predicate from a filter expression. Handles numeric, string, and regex comparisons robustly.
     *
     * @param filterExpression The filter string to parse (e.g., "@.price > 100", "@.name =~ '^A.*'").
     * @return A Predicate for filtering a stream of maps.
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
     * Safely extracts the SseEvent Sink from the ToolContext.
     * @param toolContext The context provided by the AI model.
     * @return The Sinks.Many<SseEvent> if found, otherwise null.
     */
    @SuppressWarnings("unchecked")
    private Sinks.Many<SseEvent> getSinkFromContext(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext() != null) {
            Object sinkObj = toolContext.getContext().get("sseSink");
            if (sinkObj instanceof Sinks.Many) {
                try {
                    return (Sinks.Many<SseEvent>) sinkObj;
                } catch (ClassCastException e) {
                    log.warn("Object 'sseSink' in ToolContext is not of the expected type Sinks.Many<SseEvent>.");
                }
            }
        }
        return null;
    }

    private void validateApiCallInputs(String apiName, String operationId) {
        if (StringUtils.isBlank(apiName)) throw new IllegalArgumentException("API name is required.");
        if (StringUtils.isBlank(operationId)) throw new IllegalArgumentException("Operation ID is required.");
    }
    
    /**
     * Validates the parameters provided by the LLM against the endpoint's specification.
     * Throws a ParameterValidationException if an invalid enum value is found.
     *
     * @param endpoint The API endpoint specification.
     * @param llmParameters The parameters provided by the LLM.
     */
    private void validateParametersAgainstSpec(UnifiedEndpoint endpoint, Map<String, Object> llmParameters) {
        if (endpoint.getParameters() == null || llmParameters == null || llmParameters.isEmpty()) {
            return; 
        }

        for (UnifiedParameter paramSpec : endpoint.getParameters()) {
        	if(paramSpec.getSchema() != null) {
            List<Object> validEnumValues = paramSpec.getSchema().getEnumValues();
	            if (validEnumValues != null && !validEnumValues.isEmpty() && llmParameters.containsKey(paramSpec.getName())) {
	                
	                Object providedValueObj = llmParameters.get(paramSpec.getName());
	                if (providedValueObj == null) continue;
	                
	                String providedValue = String.valueOf(providedValueObj);
	
	                boolean isValid = validEnumValues.stream()
	                    .anyMatch(enumVal -> String.valueOf(enumVal).equalsIgnoreCase(providedValue));
	                
	                if (!isValid) {
	                    log.warn("Parameter validation failed for '{}'. Provided: '{}', Allowed: {}", paramSpec.getName(), providedValue, validEnumValues);
	                    throw new ParameterValidationException(paramSpec.getName(), providedValue, validEnumValues);
	                }
	            }
        	}
        }
    }
    
    private String formatParametersForLLM(List<UnifiedParameter> parameters) {
        if (parameters == null || parameters.isEmpty()) return "  - Parameters: None";
        return "  - Parameters:\n" + parameters.stream()
            .map(p -> String.format("    - Name: %s, In: %s, Required: %s, Description: %s", p.getName(), p.getIn(), p.isRequired(), p.getDescription()))
            .collect(Collectors.joining("\n"));
    }

    private ApiCallResponse createStructuredErrorResponse(String apiName, String operationId, Exception e) {
        StructuredApiError.StructuredApiErrorBuilder errorBuilder = StructuredApiError.builder()
            .message(e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("apiNameAttempted", apiName);
        details.put("operationIdAttempted", operationId);

        int statusCode = 500;

        if (e instanceof ParameterValidationException pve) {
            errorBuilder.errorType("INVALID_PARAMETERS");
            errorBuilder.message("Invalid value '" + pve.getInvalidValue() + "' for parameter '" + pve.getParameterName() + "'.");
            
            details.put("failingParameter", pve.getParameterName());
            details.put("providedValue", pve.getInvalidValue());
            details.put("allowedValues", pve.getValidValues()); 
            
            statusCode = 400; 

        } else if (e instanceof IllegalArgumentException) {
            errorBuilder.errorType("INVALID_PARAMETERS");
            statusCode = 400; 
        } else if (e instanceof ApiExecutionException) {
            errorBuilder.errorType("API_EXECUTION_FAILED");
            statusCode = 502; 
        } else {
            errorBuilder.errorType("INTERNAL_TOOL_ERROR");
        }

        errorBuilder.details(details);

        return ApiCallResponse.failure(errorBuilder.build(), statusCode)
                .withApiDetails(apiName, operationId)
                .withExecutionTime(0L);
    }
    
    private Map<String, Object> parseParameters(String parameters) {
        if (StringUtils.isBlank(parameters) || "{}".equals(parameters.trim())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(parameters, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid parameters JSON: " + e.getMessage());
        }
    }
    
    private String resolvePath(UnifiedEndpoint endpoint, Map<String, Object> parameters) {
        String resolvedPath = endpoint.getPath();
        List<UnifiedParameter> endpointParams = endpoint.getParameters();

        if (endpointParams == null) return resolvedPath;

        for (UnifiedParameter paramDef : endpointParams) {
            if ("path".equalsIgnoreCase(paramDef.getIn())) {
                String paramName = paramDef.getName();
                if (parameters.containsKey(paramName)) {
                    resolvedPath = resolvedPath.replace("{" + paramName + "}", String.valueOf(parameters.get(paramName)));
                }
            }
        }
        return resolvedPath;
    }

    private ApiCallRequest buildApiCallRequest(UnifiedApiDocument api, UnifiedEndpoint endpoint, Map<String, Object> parameters, String conversationId) {
        String resolvedPath = resolvePath(endpoint, parameters);
        Map<String, Object> remainingParameters = new HashMap<>(parameters);
        if (endpoint.getParameters() != null) {
            endpoint.getParameters().stream()
                .filter(p -> "path".equalsIgnoreCase(p.getIn()))
                .forEach(p -> remainingParameters.remove(p.getName()));
        }
        return ApiCallRequest.builder()
                .apiName(api.getName())
                .endpoint(endpoint.getOperationId())
                .path(resolvedPath)
                .headers(new HashMap<>())
                .httpMethod(endpoint.getMethod())
                .parameters(remainingParameters)
                .conversationId(conversationId)
                .build();
    }
    
    /**
     * Retrieves a value from a nested map structure using a dot-notation key.
     *
     * @param map The map to search within.
     * @param nestedKey The dot-separated key (e.g., "shippingAddress.city").
     * @return The found value, or null if the path is invalid or the key doesn't exist.
     */
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
     * Extracts a specific part from a date string.
     *
     * @param dateString The date string to process (ISO-8601 format).
     * @param part The date component to extract ('QUARTER', 'MONTH', 'YEAR').
     * @return A string representing the extracted part or an error message.
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
            log.warn("Could not parse date after multiple attempts: {}", dateString);
            return "Invalid Date Format";
        }
    }

    /**
     * Generates a list of visually distinct colors for charts.
     *
     * @param count The number of colors to generate.
     * @return A list of color strings in "rgba(...)" format.
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