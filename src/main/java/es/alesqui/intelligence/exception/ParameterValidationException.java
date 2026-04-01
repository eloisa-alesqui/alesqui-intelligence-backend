package es.alesqui.intelligence.exception;

import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class ParameterValidationException extends RuntimeException {

    private final String parameterName;
    private final Object invalidValue;
    private final List<String> validValues;

    public ParameterValidationException(String parameterName, Object invalidValue, List<Object> validEnumObjects) {
        super("Invalid value '" + invalidValue + "' for parameter '" + parameterName + "'.");
        this.parameterName = parameterName;
        this.invalidValue = invalidValue;
        this.validValues = validEnumObjects.stream()
                                           .map(String::valueOf)
                                           .collect(Collectors.toList());
    }
    
}