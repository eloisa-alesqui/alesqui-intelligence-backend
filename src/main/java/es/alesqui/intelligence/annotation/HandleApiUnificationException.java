package es.alesqui.intelligence.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to handle API unification exceptions.
 */
@Target(ElementType.METHOD) // Indicates that this annotation can only be applied to methods
@Retention(RetentionPolicy.RUNTIME) // The annotation will be available at runtime
public @interface HandleApiUnificationException {
}
