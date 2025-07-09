package es.alesqui.postmangpt.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedEndpoint {
    private String id;
    private String name;
    private String summary;
    private String description;
    private String path;
    private String method;
    private String operationId;
    private List<String> tags;
    private List<UnifiedParameter> parameters;
    private UnifiedRequestBody requestBody;
    private Map<String, UnifiedResponse> responses;
    private Map<String, String> headers;
    private UnifiedAuthentication authentication;
    private List<UnifiedExample> examples;
    private boolean deprecated;
}
