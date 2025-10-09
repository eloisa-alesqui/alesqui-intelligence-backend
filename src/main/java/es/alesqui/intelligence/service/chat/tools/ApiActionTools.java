package es.alesqui.intelligence.service.chat.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.request.ApiCallRequest;
import es.alesqui.intelligence.dto.chat.response.ApiCallResponse;
import es.alesqui.intelligence.dto.chat.response.ChartData;
import es.alesqui.intelligence.dto.chat.response.StructuredApiError;
import es.alesqui.intelligence.exception.ApiExecutionException;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedEndpoint;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedParameter;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedTag;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.api.ApiExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiActionTools {

    private final UnifiedApiService unifiedApiService;
    private final ApiExecutionService apiExecutionService;
    private final ObjectMapper objectMapper;
    
    private final Path tempFileDir = Paths.get(System.getProperty("java.io.tmpdir"), "alesqui-intelligence-files");

    @Tool(name = "list_apis", description = "Lists all available APIs with their descriptions, tags, and a summary of their capabilities. This is the first step to take to decide which API is the most appropriate for a user's query.")
    public String listApis() {
        log.info("Executing tool: listApis");
        try {
            var apis = unifiedApiService.findActiveApis().collectList().block(Duration.ofSeconds(5));
            if (apis == null || apis.isEmpty()) {
                return "No available APIs were found.";
            }

            return "Available APIs:\n" + apis.stream()
                .map(api -> {
                    String tagsString = "none";
                    if (api.getTags() != null && !api.getTags().isEmpty()) {
                        tagsString = api.getTags().stream()
                                        .map(UnifiedTag::getName) 
                                        .collect(Collectors.joining(", "));
                    }

                    String capabilities = (api.getCapabilitiesSummary() != null && !api.getCapabilitiesSummary().isBlank())
                                          ? api.getCapabilitiesSummary()
                                          : "Not available";
                    
                    return String.format(
                        "• Name: %s\n  - Description: %s\n  - Tags: [%s]\n  - Capabilities: %s",
                        api.getName(),
                        api.getDescription(),
                        tagsString,
                        capabilities
                    );
                })
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Error in listApis tool", e);
            return "Error while trying to list APIs: " + e.getMessage();
        }
    }

    @Tool(name = "list_endpoints", description = "Gets the endpoints (operations) for a specific API, including their required parameters. You need this to know which operations can be performed before calling 'callApi'.")
    public String listEndpoints(
        @ToolParam(description = "The exact name of the API to inspect. Must be one of the names returned by the 'listApis' tool.") String apiName) {
        log.info("Executing tool: listEndpoints for API '{}'", apiName);
        try {
            UnifiedApiDocument api = unifiedApiService.findByName(apiName.trim()).block(Duration.ofSeconds(5));
            if (api == null) {
                return "Error: No API was found with the name: " + apiName;
            }
            
            if (api.getEndpoints() == null || api.getEndpoints().isEmpty()) {
                return "The API '" + apiName + "' has no available endpoints (operations).";
            }

            return "Available endpoints for the API '" + apiName + "':\n" + api.getEndpoints().stream()
                .map(e -> String.format("• OperationId: %s, Method: %s, Description: %s\n%s", 
                                      e.getOperationId(), 
                                      e.getMethod(), 
                                      e.getSummary(), 
                                      formatParametersForLLM(e.getParameters())))
                .collect(Collectors.joining("\n\n")); 
        } catch (Exception e) {
            log.error("Error in listEndpoints tool for API '{}'", apiName, e);
            return "Error listing endpoints for '" + apiName + "': " + e.getMessage();
        }
    }

    private String formatParametersForLLM(List<UnifiedParameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "  - Parameters: None";
        }
        return "  - Parameters:\n" + parameters.stream()
            .map(p -> String.format("    - Name: %s, In: %s, Required: %s, Description: %s",
                                  p.getName(),       
                                  p.getIn(),         
                                  p.isRequired(),    
                                  p.getDescription())) 
            .collect(Collectors.joining("\n"));
    }
    
    @Tool(name = "call_api", description = "Calls a specific API endpoint with the provided parameters. Use this after getting the available endpoints with 'listEndpoints'.")
    public ApiCallResponse callApi(
        @ToolParam(description = "The exact name of the API to call. Must be one of the names returned by 'listApis'.") String apiName,
        @ToolParam(description = "The operation ID of the endpoint to call. Must be one of the operation IDs returned by 'listEndpoints'.") String operationId,
        @ToolParam(description = "JSON string with the parameters for the API call. Use {} for no parameters.") String parameters,
        ToolContext toolContext) {
        
        log.info("Executing tool: callApi - API: '{}', Operation: '{}', Parameters: '{}'", apiName, operationId, parameters);
        
        try {
            // 1. Validate inputs
            if (StringUtils.isBlank(apiName)) {
                throw new IllegalArgumentException("❌ Error: API name is required");
            }
            if (StringUtils.isBlank(operationId)) {
            	throw new IllegalArgumentException("❌ Error: Operation ID is required");
            }
            
            // 2. Find the API and endpoint
            UnifiedApiDocument api = unifiedApiService.findByName(apiName.trim())
                .block(Duration.ofSeconds(5));
            if (api == null) {
            	throw new ApiExecutionException("❌ Error: No API found with name: " + apiName);
            }
            
            // 3. Find the specific endpoint
            var endpoint = api.getEndpoints().stream()
                .filter(e -> operationId.trim().equals(e.getOperationId()))
                .findFirst()
                .orElse(null);
                
            if (endpoint == null) {
            	throw new ApiExecutionException("❌ Error: No endpoint found with operation ID '" + operationId + "' in API '" + apiName + "'");
            }
            
            // 4. Parse parameters
            Map<String, Object> paramMap = parseParameters(parameters);
            
            // 5. Extract conversationId
            String conversationId = (String) toolContext.getContext().get("conversationId");
            if (conversationId == null) {
                log.warn("conversationId was not found in ToolContext.");
            }
            
            // 6. Build API call request
            ApiCallRequest apiCallRequest = buildApiCallRequest(api, endpoint, paramMap, conversationId);
            
            // 7. Execute the API call
            ApiCallResponse response = apiExecutionService.executeApiCall(apiCallRequest)
                .block(Duration.ofSeconds(30));
                
            if (response == null) {
            	throw new ApiExecutionException("❌ Error: No response received from API call");
            }
            
            // 8. Format successful response
            return response;
            
        } catch (Exception e) {
            log.error("Error in callApi tool - API: '{}', Operation: '{}'", apiName, operationId, e);
            return createStructuredErrorResponse(apiName, operationId, e);
        }
    }
    
    /**
     * Resolves the path by replacing path variables with their actual values.
     * E.g., from "/users/city/{city}" and parameters {"city": "Sevilla"} to "/users/city/Sevilla"
     */
    private String resolvePath(UnifiedEndpoint endpoint, Map<String, Object> parameters) {
        String resolvedPath = endpoint.getPath();
        List<UnifiedParameter> endpointParams = endpoint.getParameters();

        if (endpointParams == null || endpointParams.isEmpty()) {
            return resolvedPath;
        }

        for (UnifiedParameter paramDef : endpointParams) {
            // Check if the parameter is a path variable
            if ("path".equalsIgnoreCase(paramDef.getIn())) {
                String paramName = paramDef.getName();
                if (parameters.containsKey(paramName)) {
                    Object paramValue = parameters.get(paramName);
                    // Replace the placeholder {paramName} with its value
                    resolvedPath = resolvedPath.replace("{" + paramName + "}", String.valueOf(paramValue));
                }
            }
        }
        return resolvedPath;
    }

    private ApiCallResponse createStructuredErrorResponse(String apiName, String operationId, Exception e) {
        StructuredApiError.StructuredApiErrorBuilder errorBuilder = StructuredApiError.builder()
            .message(e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("apiNameAttempted", apiName);
        details.put("operationIdAttempted", operationId);

        int statusCode = 500;

        if (e instanceof IllegalArgumentException) {
            errorBuilder.errorType("INVALID_PARAMETERS");
            statusCode = 400; // Bad Request
        } else if (e instanceof ApiExecutionException) {
            errorBuilder.errorType("API_EXECUTION_FAILED");
            statusCode = 502; // Bad Gateway 
        } else {
            errorBuilder.errorType("INTERNAL_TOOL_ERROR");
        }

        errorBuilder.details(details);

        return ApiCallResponse.failure(errorBuilder.build(), statusCode)
                .withApiDetails(apiName, operationId)
                .withExecutionTime(0L);
    }

	/**
     * Parses the parameters string into a Map
     */
    private Map<String, Object> parseParameters(String parameters) {
        if (StringUtils.isBlank(parameters) || "{}".equals(parameters.trim())) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(parameters, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse parameters '{}', using empty map", parameters);
            return new HashMap<>();
        }
    }

    /**
     * Builds the ApiCallRequest from the unified API document and endpoint
     */
    private ApiCallRequest buildApiCallRequest(UnifiedApiDocument api, UnifiedEndpoint endpoint, Map<String, Object> parameters, String conversationId) {
    	
    	// Resolve path variables before building the request
        String resolvedPath = resolvePath(endpoint, parameters);
        
        // Create a mutable copy of the parameters to filter out path variables
        Map<String, Object> remainingParameters = new HashMap<>(parameters);

        // Remove parameters that were used in the path from the remaining parameters map
        if (endpoint.getParameters() != null) {
            for (UnifiedParameter paramDef : endpoint.getParameters()) {
                if ("path".equalsIgnoreCase(paramDef.getIn())) {
                    remainingParameters.remove(paramDef.getName());
                }
            }
        }
    	
        ApiCallRequest request = ApiCallRequest.builder()
                .apiName(api.getName())
                .endpoint(endpoint.getOperationId())
                .path(resolvedPath)
                .headers(new HashMap<>())
                .httpMethod(endpoint.getMethod())
                .parameters(remainingParameters)
                .conversationId(conversationId)
                .build();
        
        return request;
    }
    
    @Tool(name = "create_excel_file", description = "Creates an Excel file from JSON data and returns a download link. Use this tool when the user asks for a file (Excel, CSV, etc.) to be created.")
    public String createExcelFile(
        @ToolParam(description = "A JSON string representing an array of objects. Each object is a row, and each key in the object is a column header.") String jsonData,
        @ToolParam(description = "A descriptive name for the file, without the extension. Example: 'api_endpoints_report'") String filename
    ) {
        try {
            // 1. Ensure the temporary directory exists
            if (!Files.exists(tempFileDir)) {
                Files.createDirectories(tempFileDir);
            }

            // 2. Convert the JSON to a list of maps
            List<Map<String, Object>> data = objectMapper.readValue(jsonData, new TypeReference<>() {});

            if (data.isEmpty()) {
                return "Error: Cannot create an empty file. The JSON data was empty.";
            }

            // 3. Create the Excel workbook using Apache POI
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Data");

            // Create the header row
            Row headerRow = sheet.createRow(0);
            List<String> headers = data.get(0).keySet().stream().toList();
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
            }

            // Fill the rows with data
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                Map<String, Object> rowData = data.get(i);
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.createCell(j);
                    Object value = rowData.get(headers.get(j));
                    cell.setCellValue(value != null ? value.toString() : "");
                }
            }

            // 4. Save the file to the temporary directory
            String uniqueFilename = filename.replaceAll("[^a-zA-Z0-9.-]", "_") + "_" + UUID.randomUUID().toString().substring(0, 8) + ".xlsx";
            Path filePath = tempFileDir.resolve(uniqueFilename);

            try (FileOutputStream fileOut = new FileOutputStream(filePath.toFile())) {
                workbook.write(fileOut);
            }
            workbook.close();

            // 5. Return the file name
            return "File created successfully. [FILE=" + uniqueFilename + "]";

        } catch (Exception e) {
            // Return a clear error to the AI
            return "Error creating Excel file: " + e.getMessage();
        }
    }

    @Tool(name = "create_chart", description = "Creates a chart configuration object from JSON data. Use this when the user asks for a visual representation of data (graph, chart, plot, etc.).")
    public ChartData createChart(
        @ToolParam(description = "The type of chart to create. Supported values: 'bar', 'pie', 'line'.") String chartType,
        @ToolParam(description = "A JSON string of the data to plot. It should be an array of objects.") String jsonData,
        @ToolParam(description = "The key in the JSON objects to be used for the labels on the chart's axis.") String labelKey,
        @ToolParam(description = "The key in the JSON objects to be used for the data values.") String dataKey,
        @ToolParam(description = "A descriptive title for the dataset. Example: 'Sales per Category'") String datasetLabel
    ) {
        try {
            List<Map<String, Object>> data = objectMapper.readValue(jsonData, new TypeReference<>() {});

            List<String> labels = data.stream()
                .map(row -> row.get(labelKey).toString())
                .collect(Collectors.toList());

            List<Object> values = data.stream()
                .map(row -> row.get(dataKey))
                .collect(Collectors.toList());

            ChartData.ChartDataset dataset = ChartData.ChartDataset.builder()
                .label(datasetLabel)
                .data(values)
                .backgroundColor(generateColors(values.size())) 
                .borderWidth(1)
                .build();

            ChartData.ChartConfigData configData = ChartData.ChartConfigData.builder()
                .labels(labels)
                .datasets(List.of(dataset))
                .build();

            return ChartData.builder()
                .type(chartType)
                .data(configData)
                .options(Map.of("responsive", true)) 
                .build();

        } catch (Exception e) {
            throw new RuntimeException("Error creating chart data: " + e.getMessage());
        }
    }

    /**
     * Generates a list of aesthetically pleasing and visually distinct colors,
     * using the golden ratio to distribute the hues.
     * Ideal for any number of data points.
     *
     * @param count The number of colors to generate.
     * @return A list of color strings in "rgba(...)" format.
     */
    private List<String> generateColors(int count) {
        if (count <= 0) {
            return List.of();
        }

        final float GOLDEN_RATIO_CONJUGATE = 0.61803398875f;
        final float saturation = 0.8f; // For vivid, not pastel, colors.
        final float brightness = 0.9f; // For bright, not dark, colors.
        
        // The seed for our stream: a random starting hue.
        float initialHue = new Random().nextFloat();

        // Use Stream.iterate to generate a sequence of hues.
        // It takes a seed (initialHue) and a function to generate the next element.
        return Stream.iterate(initialHue, previousHue -> (previousHue + GOLDEN_RATIO_CONJUGATE) % 1.0f)
            .limit(count) // Limit the infinite stream to the number of colors we need.
            .map(hue -> {
                // Map each hue value to a Color object.
                Color color = Color.getHSBColor(hue, saturation, brightness);
                // Convert the color to RGBA format for Chart.js.
                return String.format("rgba(%d, %d, %d, 0.7)",
                                     color.getRed(),
                                     color.getGreen(),
                                     color.getBlue());
            })
            .collect(Collectors.toList()); // Collect the results into a list.
    }
    
    @Tool(name = "process_data", description = "Analyzes a JSON array of objects to perform operations like filtering, counting, grouping, or aggregating. Use this after calling an API to extract specific insights from the data.")
    public String processData(
        @ToolParam(description = "A JSON string representing an array of objects to be processed.") String jsonData,
        @ToolParam(description = "The operation to perform. Supported values: 'COUNT', 'FILTER', 'GROUP_BY_COUNT', 'GROUP_BY_DATE_PART_COUNT', 'SUM', 'AVERAGE'.") String operation,
        @ToolParam(description = "Optional: A filter expression, e.g., \"status=='ACTIVE'\" or \"price>100\". Supported operators: ==, !=, >, <, >=, <=.") String filterExpression,
        @ToolParam(description = "Optional: The key to group by. Required for 'GROUP_BY_COUNT', 'SUM', or 'AVERAGE' operations.") String groupByKey,
        @ToolParam(description = "Optional: The key whose values will be summed or averaged. Required for 'SUM' or 'AVERAGE' operations.") String valueKey,
        @ToolParam(description = "Optional: The key containing the date string (ISO format). Required for 'GROUP_BY_DATE_PART_COUNT'.") String dateKey,
        @ToolParam(description = "Optional: The date part to group by (e.g., 'QUARTER', 'MONTH', 'YEAR'). Required for 'GROUP_BY_DATE_PART_COUNT'.") String datePart
    ) {
        log.info("Executing tool: processData with operation '{}'", operation);
        try {
            List<Map<String, Object>> data = objectMapper.readValue(jsonData, new TypeReference<>() {});
            if (data.isEmpty()) {
                return "{\"result\": \"The provided JSON data is empty.\"}";
            }

            // 1. Create a filtered stream ONCE at the beginning
            Stream<Map<String, Object>> filteredStream = data.stream();
            if (StringUtils.isNotBlank(filterExpression)) {
                filteredStream = filteredStream.filter(createFilterPredicate(filterExpression));
            }
            // Convert the stream to a list to be reused by operations
            List<Map<String, Object>> filteredData = filteredStream.collect(Collectors.toList());

            Object result;
            switch (operation.toUpperCase()) {
                case "COUNT":
                    // The count is now performed on the pre-filtered data
                    result = Map.of("count", filteredData.size());
                    break;

                case "FILTER":
                    // The filter is already applied, just return the result
                    result = filteredData;
                    break;
                    
                case "GROUP_BY_COUNT":
                    if (StringUtils.isBlank(groupByKey)) {
                        throw new IllegalArgumentException("A 'groupByKey' is required for 'GROUP_BY_COUNT' operation.");
                    }
                    // Apply the filter BEFORE grouping
                    result = filteredData.stream()
                        .collect(Collectors.groupingBy(row -> String.valueOf(row.getOrDefault(groupByKey, "N/A")), Collectors.counting()));
                    break;
                    
                case "GROUP_BY_DATE_PART_COUNT":
                    if (StringUtils.isBlank(dateKey) || StringUtils.isBlank(datePart)) {
                        throw new IllegalArgumentException("A 'dateKey' and 'datePart' are required for 'GROUP_BY_DATE_PART_COUNT'");
                    }
                    // Apply the filter BEFORE grouping
                    result = filteredData.stream()
                        .collect(Collectors.groupingBy(row -> extractDatePart(String.valueOf(row.get(dateKey)), datePart), Collectors.counting()));
                    break;
                
                case "SUM":
                case "AVERAGE":
                    if (StringUtils.isBlank(groupByKey) || StringUtils.isBlank(valueKey)) {
                        throw new IllegalArgumentException("A 'groupByKey' and 'valueKey' are required for SUM/AVERAGE operations.");
                    }
                    
                    java.util.function.ToDoubleFunction<Map<String, Object>> mapper = 
                        row -> Double.parseDouble(String.valueOf(row.getOrDefault(valueKey, "0")));

                    if (operation.equalsIgnoreCase("SUM")) {
                        result = filteredData.stream()
                            .collect(Collectors.groupingBy(row -> String.valueOf(row.getOrDefault(groupByKey, "N/A")), 
                                                           Collectors.summingDouble(mapper)));
                    } else { // AVERAGE
                        result = filteredData.stream()
                            .collect(Collectors.groupingBy(row -> String.valueOf(row.getOrDefault(groupByKey, "N/A")), 
                                                           Collectors.averagingDouble(mapper)));
                    }
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported operation: " + operation + ". Supported operations are: COUNT, FILTER, GROUP_BY_COUNT, GROUP_BY_DATE_PART_COUNT, SUM, AVERAGE.");
            }
            
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error executing processData tool: {}", e.getMessage(), e);
            return "{\"error\": \"Failed to process data. Reason: " + e.getMessage() + "\"}";
        }
    }
    
    /**
     * Creates a Predicate from a filter expression like "key==value" or "key>value".
     * It automatically handles numeric and string comparisons.
     *
     * @param filterExpression The filter string to parse. Supported operators are
     * ==, !=, >, <, >=, <=.
     * @return A Predicate suitable for filtering a stream of maps.
     */
    private Predicate<Map<String, Object>> createFilterPredicate(String filterExpression) {
        // Regex to capture the key, operator, and value from the expression
        Pattern pattern = Pattern.compile("(.+?)(==|!=|>=|<=|>|<)(.+)");
        Matcher matcher = pattern.matcher(filterExpression.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid filter format. Expected: key[operator]value. Supported operators: ==, !=, >, <, >=, <=");
        }

        String key = matcher.group(1).trim();
        String operator = matcher.group(2).trim();
        String valueStr = matcher.group(3).trim();

        // Clean quotes from the value string
        if ((valueStr.startsWith("'") && valueStr.endsWith("'")) || (valueStr.startsWith("\"") && valueStr.endsWith("\""))) {
            valueStr = valueStr.substring(1, valueStr.length() - 1);
        }
        final String finalValue = valueStr;

        return map -> {
        	Object mapValue = getNestedValue(map, key);
            if (mapValue == null) {
                return false;
            }

            // Attempt a numeric comparison first
            try {
                double mapValueNum = Double.parseDouble(mapValue.toString());
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
                // If numeric parsing fails, fall back to a string comparison
                int comparison = mapValue.toString().compareToIgnoreCase(finalValue);
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
            if (!(currentValue instanceof Map)) {
                return null; // The path is invalid
            }
            currentValue = ((Map<String, Object>) currentValue).get(part);
            if (currentValue == null) {
                return null; // The key does not exist at this level
            }
        }
        return currentValue;
    }
    
    /**
     * Extracts a specific part (e.g., quarter, month, year) from a date string.
     * This method robustly parses date strings in ISO-8601 format, accommodating
     * inputs both with and without UTC offset information.
     *
     * @param dateString The date string to process, such as "2025-10-21T10:00:00Z"
     * or "2025-10-21T10:00:00".
     * @param part The component of the date to extract. Supported values are
     * "QUARTER", "MONTH", and "YEAR" (case-insensitive).
     * @return A string representing the extracted date part (e.g., "Quarter 3",
     * "OCTOBER", "2025"). Returns a descriptive error string if the
     * operation fails.
     */
    private String extractDatePart(String dateString, String part) {
        if (dateString == null || dateString.equals("null")) {
            return "Invalid Date";
        }
        try {
            // Use a common interface to handle both date/time types
            TemporalAccessor parsedDate;
            try {
                // 1. Attempt to parse as OffsetDateTime (with timezone info)
                parsedDate = OffsetDateTime.parse(dateString);
            } catch (DateTimeParseException e) {
                // 2. If it fails, attempt to parse as LocalDateTime (without timezone info)
                parsedDate = LocalDateTime.parse(dateString);
            }

            // Extract values using the common interface
            int monthValue = parsedDate.get(ChronoField.MONTH_OF_YEAR);
            int yearValue = parsedDate.get(ChronoField.YEAR);

            switch (part.toUpperCase()) {
                case "QUARTER":
                    int quarter = (monthValue - 1) / 3 + 1;
                    return "Quarter " + quarter;
                case "MONTH":
                    return Month.of(monthValue).toString();
                case "YEAR":
                    return String.valueOf(yearValue);
                default:
                    return "Unknown Part";
            }
        } catch (DateTimeParseException e) {
            // This catches the failure of the second (fallback) parsing attempt
            log.warn("Could not parse date after multiple attempts: {}", dateString);
            return "Invalid Date Format";
        }
    }
 
}
