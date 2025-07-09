package es.alesqui.postmangpt.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedAuthentication {
    private String type; // basic, bearer, apiKey, oauth2, etc.
    private String scheme;
    private String bearerFormat;
    private String name;
    private String in;
    private Map<String, Object> flows;
    private Map<String, String> attributes;
}
