package es.alesqui.intelligence.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import es.alesqui.intelligence.exception.ApiUnificationException;

@Aspect
@Component
public class ApiUnificationExceptionAspect {

	private static final Logger log = LoggerFactory.getLogger(ApiUnificationExceptionAspect.class);

	/**
	 * Advice that wraps methods annotated with @HandleApiException.
	 */
	@Around("@annotation(HandleApiException)")
	public Object handleApiException(ProceedingJoinPoint joinPoint) throws Throwable { 																						
		try {
			// Proceed with the method execution
			return joinPoint.proceed();
		} catch (Exception e) {
			// Log the error and wrap it in an ApiUnificationException
			log.error("Error in method {}: {}", joinPoint.getSignature(), e.getMessage(), e);
			throw new ApiUnificationException("An error occurred: " + e.getMessage(), e);
		}
	}
}
