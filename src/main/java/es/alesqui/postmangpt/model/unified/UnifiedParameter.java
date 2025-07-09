package es.alesqui.postmangpt.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedParameter {
    private String name;
    private String in; // path, query, header, cookie
    private String description;
    private boolean required;
    private String type;
    private String format;
    private Object defaultValue;
    private Object example;
    private List<Object> enumValues;
    private UnifiedSchema schema;
}